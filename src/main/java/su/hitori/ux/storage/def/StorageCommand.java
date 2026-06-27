package su.hitori.ux.storage.def;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.arguments.UUIDArgument;
import dev.jorel.commandapi.executors.CommandArguments;
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

public final class StorageCommand extends CommandAPICommand {

    private final DefaultStorageImpl storage;

    public StorageCommand(DefaultStorageImpl storage) {
        super("storage");
        this.storage = storage;

        withPermission("*");
        withSubcommands(
                new CommandAPICommand("move")
                        .withArguments(new TextArgument("old_name"), new UUIDArgument("new_uuid"), new TextArgument("new_name"), new BooleanArgument("move_game_data"))
                        .executes(this::move),

                new CommandAPICommand("util").withSubcommands(
                        new CommandAPICommand("offline-uuid")
                                .withArguments(new TextArgument("name"))
                                .executes(this::utilOfflineUuid)
                ),

                new CommandAPICommand("dump")
                        .withArguments(new TextArgument("name"))
                        .executes(this::dump),

                new CommandAPICommand("initializeServer")
                        .withArguments(new TextArgument("field"), new TextArgument("file"))
                        .executes(this::initialize),

                new CommandAPICommand("saveServer")
                        .withArguments(new TextArgument("field"), new TextArgument("file"))
                        .executes(this::save)
        );
    }

    private void initialize(CommandSender sender, CommandArguments args) {
        String fieldName = (String) args.get("field");
        assert fieldName != null;

        Set<DataField<?>> scheme = storage.serverDataScheme;

        DataField<?> field = scheme.stream()
                .filter(field0 -> field0.name().equalsIgnoreCase(fieldName)).findFirst()
                .orElse(null);
        if(field == null) {
            sender.sendMessage(Messages.ERROR.create("Can't find requested field."));
            return;
        }

        File file = new File(Bukkit.getPluginsFolder().getParentFile(), args.getOrDefaultUnchecked("file", ""));
        if(!file.exists()) {
            sender.sendMessage(Messages.ERROR.create("File doesn't exists."));
            return;
        }

        Object json = JSONUtil.readFile(file).get("encoded");
        if(json == null) {
            sender.sendMessage(Messages.ERROR.create("Encoded object is not found."));
            return;
        }

        sender.sendMessage(Messages.INFO.create("Proceeding on loading data..."));

        Object value;
        try {
            value = field.codec().decode(json);
        }
        catch (Exception exception) {
            exception.printStackTrace();
            sender.sendMessage(Messages.ERROR.create("An error has been caught."));
            return;
        }

        storage.getServerDataContainer().thenAccept(container -> {
            if(container == null) return;

            container.set(UnsafeUtil.cast(field), value);
            sender.sendMessage(Messages.INFO.create("Everything should be fine."));
        });
    }

    private void save(CommandSender sender, CommandArguments args) {
        String fieldName = (String) args.get("field");
        assert fieldName != null;

        Set<DataField<?>> scheme = storage.serverDataScheme;

        DataField<?> field = scheme.stream()
                .filter(field0 -> field0.name().equalsIgnoreCase(fieldName)).findFirst()
                .orElse(null);
        if(field == null) {
            sender.sendMessage(Messages.ERROR.create("Can't find requested field."));
            return;
        }

        File file = new File(Bukkit.getPluginsFolder().getParentFile(), args.getOrDefaultUnchecked("file", ""));

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
    }

    private void dump(CommandSender sender, CommandArguments args) {
        String name = (String) args.get("name");
        assert name != null;

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
    }

    private void utilOfflineUuid(CommandSender sender, CommandArguments args) {
        String name = (String) args.get("name");
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        sender.sendMessage(Messages.INFO.create(String.format(
                "Offline UUID (pirate) of <yellow>%s</yellow> would be <yellow><hover:show_text:'Click to copy'><click:copy_to_clipboard:%s>[%s]</yellow> <dark_gray>(click to copy)</dark_gray>",
                name,
                uuid,
                uuid
        )));
    }

    private void move(CommandSender sender, CommandArguments args) {
        String oldName = (String) args.get("old_name");
        UUID newUuid = (UUID) args.get("new_uuid");
        String newName = (String) args.get("new_name");
        boolean moveGameData = (boolean) args.getOrDefault("move_game_data", true);
        assert oldName != null && newUuid != null && newName != null;

        storage.getIdentifier(Either.ofSecond(oldName)).thenAccept(identifier -> {
            if(identifier == null) {
                sender.sendMessage(Messages.ERROR.create(UXConfiguration.I.chat.noSuchPlayer));
                return;
            }

            Task.ensureAsync(() -> {
                try {
                    movePlayerData(sender, identifier, newUuid, newName, moveGameData);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            });
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void movePlayerData(CommandSender sender, Identifier old, UUID newGameUuid, String newGameName, boolean moveGameData) throws ExecutionException, InterruptedException {
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

        sender.sendMessage(Messages.INFO.create("Removing old identifier from cache and closing data container <dark_gray>(1/3)</dark_gray>"));
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
