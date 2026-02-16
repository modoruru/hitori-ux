package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.CommandAPIArgumentType;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.hitori.api.Pair;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Task;
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

public final class IgnoreCommand extends CommandAPICommand {

    private final Chat chat;
    private final Storage storage;
    private final boolean newIgnoringState;

    public IgnoreCommand(UXModule uxModule, boolean newIgnoringState) {
        super(newIgnoringState ? "ignore" : "unignore");
        this.chat = uxModule.chat();
        this.storage = uxModule.storage();
        this.newIgnoringState = newIgnoringState;

        withSubcommands(
                new CommandAPICommand("dm").withArguments(new PlayerIgnoringArgument(uxModule, IgnoringType.DIRECT_MESSAGES, newIgnoringState)).executesPlayer((player, args) -> {
                    setIgnoring(player, args, IgnoringType.DIRECT_MESSAGES);
                }),

                new CommandAPICommand("chat").withArguments(new PlayerIgnoringArgument(uxModule, IgnoringType.CHAT, newIgnoringState)).executesPlayer((player, args) -> {
                    setIgnoring(player, args, IgnoringType.CHAT);
                }),

                new CommandAPICommand("list").executesPlayer(this::list)
        );
    }

    private void setIgnoring(Player sender, CommandArguments args, IgnoringType ignoringType) {
        String playerName = (String) args.get("player");
        assert playerName != null;

        CompletableFuture<DataContainer> senderFuture = storage.getUserDataContainer(sender);
        storage.getIdentifierByGameName(playerName)
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

    private void list(Player sender, CommandArguments args) {
        Task.ensureAsync(() -> list0(sender));
    }

    private void list0(Player sender) {
        Identifier senderId;
        try {
            senderId = storage.getIdentifierByGameName(sender.getName()).get();
        }
        catch (Throwable ex) {
            return;
        }

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
    }

    private static class SetEntry {

        boolean chat, dm;

    }

    private static class PlayerIgnoringArgument extends Argument<String> {

        protected PlayerIgnoringArgument(UXModule uxModule, IgnoringType ignoringType, boolean newIgnoringState) {
            super("player", StringArgumentType.string());
            replaceSuggestions(ArgumentSuggestions.stringCollectionAsync(info -> CompletableFuture.supplyAsync(() -> {
                if(!(info.sender() instanceof Player sender)) return Set.of();

                Identifier identifier;
                try {
                    identifier = uxModule.storage().getIdentifierByGameName(sender.getName()).get();
                }
                catch (Throwable ex) {
                    return Set.of();
                }

                Set<String> names = uxModule.chat().resolveIgnoringSetIdentifiers(identifier, ignoringType)
                        .parallelStream()
                        .map(Identifier::gameName)
                        .collect(Collectors.toSet());

                if(!newIgnoringState)
                    return names;

                return Bukkit.getOnlinePlayers().parallelStream().map(Player::getName)
                        .filter(name -> !names.contains(name)) // we hope case-sensitive list is returned
                        .toList();
            }, uxModule.executorService())));
        }

        @Override
        public Class<String> getPrimitiveType() {
            return String.class;
        }

        @Override
        public CommandAPIArgumentType getArgumentType() {
            return CommandAPIArgumentType.PRIMITIVE_TEXT;
        }

        @Override
        public <Source> String parseArgument(CommandContext<Source> cmdCtx, String key, CommandArguments previousArgs) {
            return cmdCtx.getArgument(key, String.class);
        }
    }

}
