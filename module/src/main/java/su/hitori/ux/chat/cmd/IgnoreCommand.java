package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.hitori.api.Pair;
import su.hitori.api.util.Either;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Text;
import su.hitori.ux.UXModule;
import su.hitori.ux.chat.Chat;
import su.hitori.ux.chat.IgnoringType;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class IgnoreCommand {

    private final Chat chat;
    private final UXModule uxModule;
    private final boolean newIgnoringState;

    private IgnoreCommand(UXModule uxModule, boolean newIgnoringState) {
        this.chat = uxModule.chat();
        this.uxModule = uxModule;
        this.newIgnoringState = newIgnoringState;
    }

    public static LiteralCommandNode<CommandSourceStack> bootstrap(UXModule uxModule, boolean newIgnoringState) {
        IgnoreCommand ignoreCommand = new IgnoreCommand(uxModule, newIgnoringState);

        var builder = Commands.literal(newIgnoringState ? "ignore" : "unignore")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.literal("dm")
                        .then(playerIgnoringArgument(ignoreCommand, IgnoringType.DIRECT_MESSAGES)))
                .then(Commands.literal("chat")
                        .then(playerIgnoringArgument(ignoreCommand, IgnoringType.CHAT)));

        if(newIgnoringState) {
            builder.then(Commands.literal("list")
                    .executes(ignoreCommand::list));
        }

        return builder.build();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerIgnoringArgument(IgnoreCommand ignoreCommand, IgnoringType ignoringType) {
        return Commands.argument("player", StringArgumentType.string())
                .suggests((context, builder) -> {
                    Player sender = (Player) context.getSource().getSender();

                    UXModule uxModule = ignoreCommand.uxModule;
                    return uxModule.storage().getIdentifier(Either.ofSecond(sender.getName())).thenApply(identifier -> {

                        Set<String> names = uxModule.chat().resolveIgnoringSetIdentifiers(identifier, ignoringType)
                                .parallelStream()
                                .map(Identifier::gameName)
                                .collect(Collectors.toSet());

                        if(!ignoreCommand.newIgnoringState) {
                            names.forEach(builder::suggest);
                            return builder.build();
                        }

                        Bukkit.getOnlinePlayers().parallelStream()
                                .map(Player::getName)
                                .filter(name -> !names.contains(name))
                                .forEach(builder::suggest); // we hope case-sensitive list is returned

                        return builder.build();
                    });
                })
                .executes(source -> ignoreCommand.setIgnoring(ignoringType, source));
    }

    private int setIgnoring(IgnoringType ignoringType, CommandContext<CommandSourceStack> source) {
        Player sender = (Player) source.getSource().getSender();
        String playerName = source.getArgument("player", String.class);

        Storage<DataContainer> storage = uxModule.storage();
        CompletableFuture<DataContainer> senderFuture = storage.getUserDataContainer(sender);
        storage.getIdentifier(Either.ofSecond(playerName))
                .thenCompose(identifier -> storage.getUserDataContainer(identifier, true, false))
                .thenCombine(senderFuture, (first, second) -> Pair.of(
                        Optional.ofNullable(first),
                        Optional.ofNullable(second)
                ))
                .thenAccept(pair -> {
                    DataContainer
                            targetContainer = pair.first().orElse(null),
                            senderContainer = pair.second().orElse(null);

                    if(senderContainer == null) return;

                    if(targetContainer == null) {
                        sender.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                                UXConfiguration.I.chat.noSuchPlayer,
                                Placeholder.createFinal("player_name", playerName)
                        )));
                        return;
                    }

                    setIgnoring0(sender, senderContainer, targetContainer, ignoringType);
                });

        return 1;
    }

    private void setIgnoring0(Player sender, DataContainer senderContainer, DataContainer targetContainer, IgnoringType ignoringType) {
        var config = UXConfiguration.I.chat.ignoring;

        if(senderContainer == targetContainer) {
            sender.sendMessage(Messages.ERROR.create(config.cantIgnoreYourself));
            return;
        }

        if(newIgnoringState) {
            for (String resistant : config.ignoringResistant) {
                if(resistant.equalsIgnoreCase(targetContainer.identifier().gameName())) {
                    sender.sendMessage(Messages.ERROR.create(
                            config.tryToIgnoreResistant.convert().determine(targetContainer)
                    ));
                    return;
                }
            }
        }

        boolean isIgnoring = chat.isIgnoring(senderContainer.identifier(), targetContainer.identifier(), ignoringType);
        Placeholder[] placeholders = {
                Placeholder.createFinal("ignored_name", targetContainer.identifier().gameName()),
                Placeholder.create("ignoring_type", () -> switch(ignoringType) {
                    case CHAT -> config.chat;
                    case DIRECT_MESSAGES -> config.directMessages;
                })
        };

        if(isIgnoring == newIgnoringState) {
            sender.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                    (newIgnoringState ? config.alreadyIgnored : config.notIgnored),
                    placeholders
            )));
            return;
        }

        chat.setIgnoring(senderContainer.identifier(), targetContainer.identifier(), ignoringType, newIgnoringState);
        sender.sendMessage(Messages.INFO.create(Placeholders.resolve(
                (newIgnoringState ? config.ignoredNow : config.unignoredNow),
                placeholders
        )));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        Player sender = (Player) context.getSource().getSender();

        uxModule.storage().getIdentifier(Either.ofSecond(sender.getName())).thenAccept(senderId -> {
            var config = UXConfiguration.I.chat.ignoring.list;

            Set<Identifier>
                    chatSet = chat.resolveIgnoringSetIdentifiers(senderId, IgnoringType.CHAT),
                    dmSet = chat.resolveIgnoringSetIdentifiers(senderId, IgnoringType.DIRECT_MESSAGES);

            if(chatSet.isEmpty() && dmSet.isEmpty()) {
                sender.sendActionBar(Text.create(config.notIgnoreAnyone));
                return;
            }

            Map<Identifier, SetEntry> map = new HashMap<>();
            for (Identifier entry : chatSet) {
                map.computeIfAbsent(entry, (_) -> new SetEntry()).chat = true;
            }

            for (Identifier entry : dmSet) {
                map.computeIfAbsent(entry, (_) -> new SetEntry()).dm = true;
            }

            StringBuilder entries = new StringBuilder();
            var iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Identifier, SetEntry> entry = iterator.next();
                entries.append(Placeholders.resolve(
                        config.entryFormat,
                        Placeholder.create("ignored_name", () -> entry.getKey().gameName()),
                        Placeholder.create("ignoring_type", () -> {
                            SetEntry setEntry = entry.getValue();
                            return setEntry.dm && setEntry.chat
                                    ? config.both
                                    : (setEntry.dm ? config.directMessages : config.chat);
                        }),
                        Placeholder.createFinal("c", iterator.hasNext() ? ", " : ""),
                        Placeholder.createFinal("n", iterator.hasNext() ? "\n" : "")
                ));
            }

            sender.sendMessage(Messages.INFO.create(Placeholders.resolve(
                    config.listFormat,
                    Placeholder.createFinal("n", map.isEmpty() ? "" : "\n"),
                    Placeholder.create("entries", entries::toString)
            )));
        });

        return 1;
    }

    private static class SetEntry {

        boolean chat, dm;

    }

}
