package su.hitori.ux.stream;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import su.hitori.api.util.Task;
import su.hitori.api.util.Text;
import su.hitori.ux.Sound;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.DataContainer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

public final class Streams {

    public static final DataField<JSONObject> ONGOING_STREAMS_FIELD = new DataField<>("ongoing_streams", DataField.castCodec());

    private final UXModule uxModule;
    private final Set<String> allowedDomains;

    private final Map<Identifier, StreamInfo> ongoingStreams;

    public Streams(UXModule uxModule) {
        this.uxModule = uxModule;
        this.allowedDomains = new HashSet<>();
        this.ongoingStreams = new HashMap<>();
        for (String allowedDomain : UXConfiguration.I.streams.allowedDomains) {
            allowedDomains.add(allowedDomain.toLowerCase());
        }

        uxModule.storage().getServerDataContainer().thenAccept(this::load);
    }

    public void load() {
        uxModule.storage().getServerDataContainer().thenAccept(this::load);
    }

    private void load(DataContainer serverContainer) {
        JSONObject ongoingStreamsJson = serverContainer.get(ONGOING_STREAMS_FIELD);
        if(ongoingStreamsJson == null) return;

        for (String key : ongoingStreamsJson.keySet()) {
            URL url;
            try {
                url = URI.create(ongoingStreamsJson.getString(key)).toURL();
            } catch (MalformedURLException e) {
                continue;
            }

            uxModule.storage().getIdentifierByUUID(UUID.fromString(key)).thenAccept(identifier ->
                ongoingStreams.put(identifier, new StreamInfo(identifier, url))
            );
        }
    }

    private void submitUpdate() {
        uxModule.storage().getServerDataContainer().thenAccept(container -> {
            JSONObject json = new JSONObject();
            for (Map.Entry<Identifier, StreamInfo> entry : ongoingStreams.entrySet()) {
                json.put(
                        entry.getKey().uuid().toString(),
                        entry.getValue().url().toString()
                );
            }
            container.set(ONGOING_STREAMS_FIELD, json);
        });
    }

    static String getDomainName(URL url) {
        String host = url.getHost();
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    Set<String> allowedDomains() {
        return allowedDomains;
    }

    public boolean isAllowed(URL url) {
        return allowedDomains.contains(getDomainName(url).toLowerCase());
    }

    public @NotNull Map<Identifier, StreamInfo> getOngoingStreams() {
        return Map.copyOf(ongoingStreams);
    }

    public StreamInfo getStream(Identifier streamer) {
        return ongoingStreams.get(streamer);
    }

    public void startStream(StreamInfo streamInfo) {
        if(getStream(streamInfo.streamer()) != null) return; // should not happen but since this method is public we should recheck
        Identifier identifier = streamInfo.streamer();

        ongoingStreams.put(identifier, streamInfo);
        submitUpdate();

        Task.ensureAsync(() -> {
            var players = Bukkit.getOnlinePlayers();
            if(players.isEmpty()) return; // should not happen also?

            Component message = Text.create(Placeholders.resolve(
                    UXConfiguration.I.streams.start,
                    Placeholder.create("player_name", identifier::gameName),
                    Placeholder.create("url_domain", () -> getDomainName(streamInfo.url())),
                    Placeholder.create("url_full", streamInfo::url)
            ));
            Sound sound = UXConfiguration.I.streams.startSound.convert();
            players.forEach(player -> {
                AsyncStreamStartMessageEvent event = new AsyncStreamStartMessageEvent(
                        streamInfo,
                        player,
                        message,
                        sound
                );
                if(!event.callEvent()) return;

                player.sendMessage(event.message());

                Sound sound1 = event.sound();
                if(sound1 != null) sound1.playFor(player);
            });
        });
    }

    public void endStream(Identifier identifier) {
        StreamInfo info = ongoingStreams.remove(identifier);
        if(info != null) submitUpdate();

        var players = Bukkit.getOnlinePlayers();
        if(players.isEmpty()) return;

        Component message = Text.create(Placeholders.resolveDynamic(
                UXConfiguration.I.streams.end,
                identifier,
                DynamicPlaceholder.create("player_name", Identifier::gameName)
        ));
        players.forEach(player -> player.sendMessage(message));
    }

}
