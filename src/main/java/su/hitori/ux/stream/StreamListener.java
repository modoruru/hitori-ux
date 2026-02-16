package su.hitori.ux.stream;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import su.hitori.api.util.Text;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.def.AsyncPlayerSynchronizationEvent;

import java.net.URL;

public final class StreamListener implements Listener {

    private final Streams streams;

    public StreamListener(Streams streams) {
        this.streams = streams;
    }

    @EventHandler
    private void onAsyncPlayerSynchronization(AsyncPlayerSynchronizationEvent event) {
        var ongoingStreams = streams.getOngoingStreams();
        if(ongoingStreams.isEmpty()) return;
        else if (ongoingStreams.size() == 1 && event.identifier().equals(ongoingStreams.keySet().iterator().next())) return;

        var config = UXConfiguration.I.streams.ongoingStreams;
        var iterator = ongoingStreams.values().iterator();
        StringBuilder entries = new StringBuilder();
        while (iterator.hasNext()) {
            StreamInfo info = iterator.next();
            URL url = info.url();
            entries.append(Placeholders.resolve(
                    config.entryFormat,
                    Placeholder.create("streamer_name", info.streamer()::gameName),
                    Placeholder.create("url_full", url::toString),
                    Placeholder.create("url_domain", () -> Streams.getDomainName(url)),
                    Placeholder.createFinal("c", iterator.hasNext() ? ", " : ""),
                    Placeholder.createFinal("n", iterator.hasNext() ? "\n" : "")
            ));
        }

        event.player().sendMessage(Text.create(Placeholders.resolve(
                config.listFormat,
                Placeholder.create("entries", entries::toString)
        )));
    }

}
