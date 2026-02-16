package su.hitori.ux.event;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Task;
import su.hitori.api.util.Text;
import su.hitori.ux.Sound;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.api.DataContainer;
import su.hitori.ux.storage.serialize.JSONCodec;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public final class Events {

    static final int MAX_EVENTS = 3;
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");
    static ZoneId ZONE_ID;

    private static final Logger LOGGER = LoggerFactory.instance().create(Events.class);

    private static final DynamicPlaceholder<Event>[] EVENT_PLACEHOLDERS = new DynamicPlaceholder[]{
            DynamicPlaceholder.create("event_name", Event::name),
            DynamicPlaceholder.create("event_description", Event::description),
            DynamicPlaceholder.<Event>create("event_start_time", (event) -> DATE_FORMAT.format(Instant.ofEpochMilli(event.utcStartTime()).atZone(ZONE_ID))),
            DynamicPlaceholder.<Event>create("event_uuid", event -> event.uuid().toString()),
    };

    private static final JSONCodec<Event> EVENT_CODEC = new JSONCodec<>(
            event -> new JSONObject()
                    .put("uuid", event.uuid().toString())
                    .put("name", event.name())
                    .put("description", event.description())
                    .put("utc_start_time", event.utcStartTime()),
            object -> {
                JSONObject json = (JSONObject) object;
                return new Event(
                        UUID.fromString(json.getString("uuid")),
                        json.getString("name"),
                        json.getString("description"),
                        json.getLong("utc_start_time")
                );
            }
    );

    public static final DataField<List<UUID>> HIDDEN_EVENTS_FIELD = DataField.createTypedList(
            "hidden_events",
            new JSONCodec<>(
                    UUID::toString,
                    obj -> UUID.fromString((String) obj)
            )
    );

    public static final DataField<List<Event>> ACTIVE_EVENTS_FIELD = DataField.createTypedList(
            "active_events", EVENT_CODEC
    );

    private final ExecutorService executorService;
    final UXModule uxModule;

    private boolean loaded;
    private boolean loading;
    private Map<UUID, Event> activeEvents;
    private Task onStartReminderTask;

    public Events(UXModule uxModule) {
        this.executorService = uxModule.executorService();
        this.uxModule = uxModule;

        String rawTimeZone = UXConfiguration.I.events.timeZone;
        TimeZone timeZone = TimeZone.getTimeZone(rawTimeZone);
        if(timeZone == null) {
            LOGGER.warning("Time zone from config is not found: \"%s\", UTC will be used.".formatted(rawTimeZone));
            ZONE_ID = ZoneId.of("UTC");
        }
        else ZONE_ID = timeZone.toZoneId();
    }

    public void load() {
        if(loaded || loading) return;

        loading = true;
        uxModule.storage().getServerDataContainer().thenAccept(container -> {
            loaded = true;
            loading = false;
            this.activeEvents = new HashMap<>();
            List<Event> activeEvents = container.get(ACTIVE_EVENTS_FIELD);
            if(activeEvents != null) {
                for (Event event : activeEvents) {
                    this.activeEvents.put(event.uuid(), event);
                }
            }
            onStartReminderTask = Task.runTaskTimerAsync(this::onStartReminderTick, 0L, 20L);
        });
    }

    boolean isHidden(DataContainer container, UUID uuid) {
        List<UUID> hidden = container.get(HIDDEN_EVENTS_FIELD);
        return hidden != null && hidden.contains(uuid);
    }

    private void onStartReminderTick() {
        long now = System.currentTimeMillis();
        for (Event event : activeEvents.values()) {
            long diff = now - event.utcStartTime();
            if(diff <= 0 || diff >= 1000) continue;

            Bukkit.getOnlinePlayers().forEach(player -> {
                executorService.execute(() -> uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
                    if(container == null || isHidden(container, event.uuid())) return;

                    showReminder(player, event);
                }));
            });
        }
    }

    public void unload() {
        if(!loaded) return;
        loaded = false;
        onStartReminderTask.cancel();
        onStartReminderTask = null;
        save();
    }

    public void planEvent(Event event) {
        if(activeEvents == null || activeEvents.size() >= MAX_EVENTS) return;

        activeEvents.put(event.uuid(), event);
        save();

        executorService.execute(() -> Bukkit.getOnlinePlayers().forEach(player -> showReminder(player, event)));
    }

    void showEventsList(Player player, List<Event> events) {
        var config = UXConfiguration.I.events.list;

        StringBuilder entries = new StringBuilder();
        var iterator = events.iterator();
        while (iterator.hasNext()) {
            Event entry = iterator.next();
            entries.append(Placeholders.resolve(
                    config.entryFormat,
                    Placeholder.create("event_name", entry::name),
                    Placeholder.create("event_uuid", () -> entry.uuid().toString()),
                    Placeholder.createFinal("c", iterator.hasNext() ? ", " : ""),
                    Placeholder.createFinal("n", iterator.hasNext() ? "\n" : "")
            ));
        }

        player.sendMessage(Messages.INFO.create(Placeholders.resolve(
                config.listFormat,
                Placeholder.create("entries", entries::toString)
        )));
    }

    void showReminder(Player player, Event event) {
        var config = UXConfiguration.I.events;

        AsyncEventReminderEvent bukkitEvent = new AsyncEventReminderEvent(
                event,
                player,
                config.reminderSound.convert()
        );
        if(!bukkitEvent.callEvent()) return;

        player.sendMessage(Text.create(Placeholders.resolveDynamic(
                Instant.now().isBefore(Instant.ofEpochMilli(event.utcStartTime()))
                        ? config.reminder
                        : config.eventHappening,
                event,
                EVENT_PLACEHOLDERS
        )));

        Sound sound = bukkitEvent.sound();
        if(sound != null) sound.playFor(player);
    }

    public void endEvent(Event event) {
        if(activeEvents == null || activeEvents.remove(event.uuid()) == null) return;

        save();

        // send message about event end
        var players = Bukkit.getOnlinePlayers();
        if(players.isEmpty()) return;

        Component message = Text.create(Placeholders.resolveDynamic(
                UXConfiguration.I.events.ended,
                event,
                EVENT_PLACEHOLDERS
        ));
        players.forEach(player -> player.sendMessage(message));
    }

    public CompletableFuture<List<Event>> getNonHiddenActiveEvents(Player player) {
        return uxModule.storage().getUserDataContainer(player).thenApply(container -> {
            List<UUID> hidden = container.get(HIDDEN_EVENTS_FIELD);
            if(hidden == null) return activeEvents();

            return activeEvents.values().parallelStream()
                    .filter(event -> !hidden.contains(event.uuid()))
                    .toList();
        });
    }

    public Event getEvent(UUID uuid) {
        return activeEvents.get(uuid);
    }

    public List<Event> activeEvents() {
        return List.copyOf(activeEvents.values());
    }

    private void save() {
        uxModule.storage().getServerDataContainer().thenAccept(
                container -> container.set(ACTIVE_EVENTS_FIELD, List.copyOf(activeEvents.values()))
        );
    }

}
