package su.hitori.ux.storage.def;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.json.JSONObject;
import su.hitori.api.util.*;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.serialize.JSONCodec;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class StorageCommand {

    private StorageCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(DefaultStorageImpl storage) {
        return Commands.literal("storage")
                .requires(source -> source.getSender().hasPermission("*"))
                .then(Commands.literal("move")
                        .then(Commands.argument("old_name", StringArgumentType.string())
                                .then(Commands.argument("new_uuid", ArgumentTypes.uuid())
                                        .then(Commands.argument("new_name", StringArgumentType.string())
                                                .then(Commands.argument("move_game_data", BoolArgumentType.bool())
                                                        .executes(context -> move(storage, context)))))))
                .then(Commands.literal("initializeServer")
                        .then(Commands.argument("field", StringArgumentType.string())
                                .then(Commands.argument("file", StringArgumentType.string())
                                        .executes(context -> initialize(storage, context)))))
                .then(Commands.literal("dump")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> dump(storage, context))))
                .then(Commands.literal("util")
                        .then(Commands.literal("offline-uuid")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(StorageCommand::utilOfflineUuid))))
                .then(Commands.literal("saveServer")
                        .then(Commands.argument("field", StringArgumentType.string())
                                .then(Commands.argument("file", StringArgumentType.string())
                                        .executes(context -> save(storage, context)))))
                .build();
    }

    private static int initialize(DefaultStorageImpl storage, CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        String fieldName = context.getArgument("field", String.class);

        Set<DataField<?>> scheme = storage.serverDataScheme;

        DataField<?> field = scheme.stream()
                .filter(field0 -> field0.name().equalsIgnoreCase(fieldName)).findFirst()
                .orElse(null);
        if(field == null) {
            sender.sendMessage(Messages.ERROR.create("Can't find requested field."));
            return 0;
        }

        File file = new File(Bukkit.getPluginsFolder().getParentFile(), context.getArgument("file", String.class));
        if(!file.exists()) {
            sender.sendMessage(Messages.ERROR.create("File doesn't exists."));
            return 0;
        }

        Object json = JSONUtil.readFile(file).get("encoded");
        if(json == null) {
            sender.sendMessage(Messages.ERROR.create("Encoded object is not found."));
            return 0;
        }

        sender.sendMessage(Messages.INFO.create("Proceeding on loading data..."));

        Object value;
        try {
            value = field.codec().decode(json);
        }
        catch (Exception exception) {
            exception.printStackTrace();
            sender.sendMessage(Messages.ERROR.create("An error has been caught."));
            return 0;
        }

        storage.getServerDataContainer().thenAccept(container -> {
            if(container == null) return;

            container.set(UnsafeUtil.cast(field), value);
            sender.sendMessage(Messages.INFO.create("Everything should be fine."));
        });

        return 1;
    }

    private static int save(DefaultStorageImpl storage, CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        String fieldName = context.getArgument("field", String.class);

        Set<DataField<?>> scheme = storage.serverDataScheme;

        DataField<?> field = scheme.stream()
                .filter(field0 -> field0.name().equalsIgnoreCase(fieldName)).findFirst()
                .orElse(null);
        if(field == null) {
            sender.sendMessage(Messages.ERROR.create("Can't find requested field."));
            return 0;
        }

        File file = new File(Bukkit.getPluginsFolder().getParentFile(), context.getArgument("file", String.class));

        storage.getServerDataContainer().thenAccept(container -> {
            if(container == null) return;

            Object object = container.get(field);
            if(object == null) return;

            Object encoded;
            try {
                encoded = UnsafeUtil.<JSONCodec<Object>>cast(field.codec()).encode(object);
            }
            catch (Exception exception) {
                exception.printStackTrace();
                return;
            }

            JSONObject json = new JSONObject().put("encoded", encoded);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString());
                writer.flush();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            sender.sendMessage(Messages.INFO.create("Everything should be fine."));
        });

        return 1;
    }

    private static int dump(DefaultStorageImpl storage, CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = context.getArgument("name", String.class);

        storage.getIdentifier(Either.ofSecond(name)).thenCompose(identifier -> storage.getUserDataContainer(identifier, true, false)).thenAccept(container -> {
            if(container == null) {
                sender.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                        UXConfiguration.I.chat.noSuchPlayer,
                        Placeholder.createFinal("player_name", name)
                )));
                return;
            }

            JSONObject json = container.encode();
            if(json == null || json.isEmpty()) {
                sender.sendMessage(Messages.INFO.create("Player data is empty!"));
                return;
            }

            String dump = json.toString(2);
            sender.sendMessage(Messages.INFO.create("Dump for <yellow>%s</yellow>:\n%s".formatted(
                    container.identifier().gameName(),
                    dump
            )));
        });

        return 1;
    }

    private static int utilOfflineUuid(CommandContext<CommandSourceStack> context) {
        String name = context.getArgument("name", String.class);
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        context.getSource().getSender().sendMessage(Messages.INFO.create(String.format(
                "Offline UUID (pirate) of <yellow>%s</yellow> would be <yellow><hover:show_text:'Click to copy'><click:copy_to_clipboard:%s>[%s]</yellow> <dark_gray>(click to copy)</dark_gray>",
                name,
                uuid,
                uuid
        )));
        return 1;
    }

    private static int move(DefaultStorageImpl storage, CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        String oldName = context.getArgument("old_name", String.class);
        UUID newUuid = context.getArgument("new_uuid", UUID.class);
        String newName = context.getArgument("new_name", String.class);
        boolean moveGameData = context.getArgument("move_game_data", boolean.class);

        storage.getIdentifier(Either.ofSecond(oldName)).thenAccept(identifier -> {
            if(identifier == null) {
                sender.sendMessage(Messages.ERROR.create(UXConfiguration.I.chat.noSuchPlayer));
                return;
            }

            Task.ensureAsync(() -> {
                try {
                    movePlayerData(storage, sender, identifier, newUuid, newName, moveGameData);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            });
        });

        return 1;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void movePlayerData(DefaultStorageImpl storage, CommandSender sender, Identifier old, UUID newGameUuid, String newGameName, boolean moveGameData) throws ExecutionException, InterruptedException {
        Identifier id0 = storage.getIdentifier(Either.ofSecond(newGameName)).get(), id1 = null;

        if(id0 != null || (id1 = storage.getIdentifier(Either.ofSecond(newGameName)).get()) != null) {
            boolean gameUuidConnected = id0 != null;
            sender.sendMessage(Messages.ERROR.create(String.format(
                    "Can't move player data with %s %s, it's already connected to %s",
                    gameUuidConnected ? newGameUuid.toString() : '"' + newGameName + '"',
                    gameUuidConnected ? "game_uuid" : "game_name",
                    gameUuidConnected ? id0 : id1
            )));
            return;
        }

        if(storage.getPlayerByIdentifier(old) != null) {
            sender.sendMessage(Messages.ERROR.create("Player should be offline!"));
            return;
        }

        sender.sendMessage(Messages.INFO.create("Removing old identifier from the cache and closing data container <dark_gray>(1/3)</dark_gray>"));
        storage.quit(old);

        sender.sendMessage(Messages.INFO.create("Inserting new data to database... <dark_gray>(2/3)</dark_gray>"));
        storage.updateIdentifier(old.uuid(), newGameUuid, newGameName);

        sender.sendMessage(Messages.INFO.create(
                (moveGameData
                        ? "Moving game data (inventory, advancements, etc)... "
                        : "Moving game data is <yellow>skipped</yellow>! ") + "<dark_gray>(3/3)</dark_gray>"
        ));
        if(moveGameData) {
            File folder = new File(Bukkit.getServer().getWorldContainer(), "world/playerdata/");
            File datFile = new File(folder, old.gameUuid() + ".dat");
            File datOldFile = new File(folder, old.gameUuid() + ".dat_old");

            if(datFile.exists()) datFile.renameTo(new File(folder, newGameUuid + ".dat"));
            if(datOldFile.exists()) datOldFile.renameTo(new File(folder, newGameUuid + ".dat_old"));
        }
    }

}
