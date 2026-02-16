package su.hitori.ux.event;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.arguments.UUIDArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import su.hitori.api.command.DateArgument;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Task;
import su.hitori.ux.config.UXConfiguration;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EventCommand extends CommandAPICommand {

    private final Events events;

    public EventCommand(Events events) {
        super("event");
        this.events = events;

        withSubcommands(
                new CommandAPICommand("plan").withArguments(
                        new TextArgument("name"),
                        new TextArgument("description"),
                        new DateArgument("start_time", Events.DATE_FORMAT, Events.ZONE_ID, true)
                ).executes(this::plan), // plans new event

                new CommandAPICommand("ok")
                        .withArguments(new UUIDArgument("uuid"))
                        .executesPlayer(this::ok), // hides notification about event

                new CommandAPICommand("end")
                        .withPermission("*")
                        .withArguments(new UUIDArgument("uuid"))
                        .executes(this::end), // ends event,

                new CommandAPICommand("uuids")
                        .withPermission("*")
                        .executes(this::uuids),

                new CommandAPICommand("view")
                        .withArguments(new UUIDArgument("uuid"))
                        .executesPlayer(this::view)
        );
    }

    private void plan(CommandSender sender, CommandArguments args) {
        if(events.activeEvents().size() >= Events.MAX_EVENTS) {
            sender.sendMessage(Messages.ERROR.create(UXConfiguration.I.events.alreadyPlanned));
            return;
        }

        String name = (String) args.get("name");
        String description = (String) args.get("description");
        ZonedDateTime date = (ZonedDateTime) args.get("start_time");
        assert name != null && description != null && date != null;

        events.planEvent(new Event(
                UUID.randomUUID(),
                name,
                description,
                date.toInstant().toEpochMilli()
        ));
    }

    private void ok(Player sender, CommandArguments args) {
        var config = UXConfiguration.I.events;
        Event event = events.getEvent((UUID) args.get("uuid"));
        if(event == null) {
            sender.sendMessage(Messages.ERROR.create(config.noEvent));
            return;
        }

        events.storage.getUserDataContainer(sender).thenAccept(container -> {
            if(events.isHidden(container, event.uuid())) return;

            List<UUID> hidden = container.get(Events.HIDDEN_EVENTS_FIELD);

            if(hidden != null) hidden.removeIf(uuid -> events.getEvent(uuid) == null);
            else hidden = new ArrayList<>();

            hidden.add(event.uuid());

            container.set(Events.HIDDEN_EVENTS_FIELD, hidden);
            sender.sendMessage(Messages.INFO.create(config.hidden));
        });
    }

    private void end(CommandSender sender, CommandArguments args) {
        var config = UXConfiguration.I.events;
        Event event = events.getEvent((UUID) args.get("uuid"));
        if(event == null) {
            sender.sendMessage(Messages.ERROR.create(config.noEvent));
            return;
        }

        events.endEvent(event);
    }

    private void uuids(CommandSender sender, CommandArguments args) {
        var events = this.events.activeEvents();
        if(events.isEmpty()) {
            sender.sendMessage(Messages.ERROR.create("There's no active events!"));
            return;
        }

        StringBuilder builder = new StringBuilder("UUID's of events:");
        for (Event event : events) {
            builder.append("\n - \"").append(event.name()).append("\", ");
            builder.append("UUID: <click:copy_to_clipboard:").append(event.uuid().toString()).append("><yellow>[click to copy]</click>");
        }

        sender.sendMessage(Messages.INFO.create(builder.toString()));
    }

    private void view(Player sender, CommandArguments args) {
        Event event = events.getEvent((UUID) args.get("uuid"));
        if(event == null) return;
        Task.ensureAsync(() -> events.showReminder(sender, event));
    }

}
