package su.hitori.ux.stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import su.hitori.api.util.Either;
import su.hitori.api.util.Messages;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.permission.DefaultPermission;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.util.URLArgumentType;

import java.net.URL;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class StreamCommand {

    private StreamCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(UXModule uxModule) {
        Streams streams = uxModule.streams();
        Predicate<CommandSourceStack> onlyPlayer = source -> source.getSender() instanceof Player;

        return Commands.literal("stream")
                .requires(source -> DefaultPermission.STREAM_TOGGLE.hasPermission(source.getSender()))
                .then(Commands.literal("start")
                        .requires(onlyPlayer)
                        .then(Commands.argument("url", URLArgumentType.url())
                                .executes(context -> {
                                    Player player = (Player) context.getSource().getSender();
                                    resolveThenContinue(uxModule, player.getName(), identifier -> start(streams, player, identifier, context.getArgument("url", URL.class)));
                                    return 1;
                                })))
                .then(Commands.literal("stop")
                        .requires(onlyPlayer)
                        .executes(context -> {
                            Player player = (Player) context.getSource().getSender();
                            resolveThenContinue(uxModule, player.getName(), identifier -> stop(streams, player, identifier));
                            return 1;
                        }))
                .then(Commands.literal("forcestop")
                        .requires(source -> source.getSender().hasPermission("*"))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests((_, builder) -> {
                                    streams.getOngoingStreams().keySet()
                                            .parallelStream()
                                            .map(Identifier::gameName)
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    resolveThenContinue(uxModule, player, identifier -> forceStop(streams, context.getSource().getSender(), player, identifier));
                                    return 1;
                                })))
                .build();
    }

    private static void resolveThenContinue(UXModule uxModule, String playerName, Consumer<Identifier> consumer) {
        uxModule.storage().getIdentifier(Either.ofSecond(playerName)).thenAccept(consumer);
    }

    private static void start(Streams streams, Player sender, Identifier identifier, URL url) {
        var config = UXConfiguration.I.streams;

        if(streams.getStream(identifier) != null) {
            sender.sendMessage(Messages.ERROR.create(config.alreadyStarted));
            return;
        }

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

    private static void stop(Streams streams, Player sender, Identifier identifier) {
        var config = UXConfiguration.I.streams;

        if(streams.getStream(identifier) == null) {
            sender.sendMessage(Messages.ERROR.create(config.noStream));
            return;
        }

        sender.sendMessage(Messages.INFO.create(config.youHaveEnded));
        streams.endStream(identifier);
    }

    private static void forceStop(Streams streams, CommandSender sender, String name, Identifier player) {
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
