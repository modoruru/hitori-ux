package su.hitori.ux.stream;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import su.hitori.api.command.URLArgument;
import su.hitori.api.util.Messages;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.permission.DefaultPermission;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.Identifier;

import java.net.URL;
import java.util.Iterator;
import java.util.function.Consumer;

public final class StreamCommand extends CommandAPICommand {

    private final UXModule uxModule;
    private final Streams streams;

    public StreamCommand(UXModule uxModule) {
        super("stream");
        this.uxModule = uxModule;
        this.streams = uxModule.streams();

        withPermission(DefaultPermission.STREAM_TOGGLE.asString());
        withSubcommands(
                new CommandAPICommand("start")
                        .withArguments(new URLArgument("url"))
                        .executesPlayer((player, args) -> {
                            resolveThenContinue(player.getName(), identifier -> start(player, identifier, args));
                        }),

                new CommandAPICommand("stop")
                        .executesPlayer((player, args) -> {
                            resolveThenContinue(player.getName(), identifier -> stop(player, identifier, args));
                        }),

                new CommandAPICommand("forcestop")
                        .withPermission("*")
                        .withArguments(new TextArgument("player").replaceSuggestions(ArgumentSuggestions.stringCollection(
                                _ ->
                                    streams.getOngoingStreams().keySet()
                                            .parallelStream()
                                            .map(Identifier::gameName)
                                            .toList()
                        )))
                        .executes((sender, args) -> {
                            String player = (String) args.get("player");
                            resolveThenContinue(player, (identifier) -> forceStop(sender, player, identifier));
                        })
        );
    }

    private void resolveThenContinue(String playerName, Consumer<Identifier> consumer) {
        uxModule.storage().getIdentifierByGameName(playerName).thenAccept(consumer);
    }

    private void start(Player sender, Identifier identifier, CommandArguments args) {
        var config = UXConfiguration.I.streams;

        if(streams.getStream(identifier) != null) {
            sender.sendMessage(Messages.ERROR.create(config.alreadyStarted));
            return;
        }

        URL url = (URL) args.get("url");
        assert url != null;
        if(!streams.isAllowed(url)) {
            sender.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                    config.nonAllowedDomain,
                    Placeholder.create("url_domain", () -> Streams.getDomainName(url)),
                    Placeholder.create("allowed_domains", () -> {
                        StringBuilder builder = new StringBuilder();

                        Iterator<String> iterator = streams.allowedDomains().iterator();
                        while (iterator.hasNext()) {
                            builder.append(iterator.next());
                            if(iterator.hasNext()) builder.append(", ");
                        }

                        return builder.toString();
                    })
            )));
            return;
        }

        sender.sendMessage(Messages.INFO.create(config.youHaveStarted));
        streams.startStream(new StreamInfo(identifier, url));
    }

    private void stop(Player sender, Identifier identifier, CommandArguments args) {
        var config = UXConfiguration.I.streams;

        if(streams.getStream(identifier) == null) {
            sender.sendMessage(Messages.ERROR.create(config.noStream));
            return;
        }

        sender.sendMessage(Messages.INFO.create(config.youHaveEnded));
        streams.endStream(identifier);
    }

    private void forceStop(CommandSender sender, String name, Identifier player) {
        Placeholder namePlaceholder = Placeholder.create("player_name", () -> name);

        if(player == null) {
            sender.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                    UXConfiguration.I.chat.noSuchPlayer,
                    namePlaceholder
            )));
            return;
        }

        var config = UXConfiguration.I.streams;

        if(streams.getStream(player) == null) {
            sender.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                    config.noStreamAdmin,
                    namePlaceholder
            )));
            return;
        }

        sender.sendMessage(Messages.INFO.create(Placeholders.resolve(
                config.youHaveEndedAdmin,
                namePlaceholder
        )));
        streams.endStream(player);
    }

}
