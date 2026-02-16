package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Storage;

public final class SpyCommand extends CommandAPICommand {

    public static final DataField<Boolean> SPYING_FIELD = DataField.createBoolean("spying");

    private final Storage storage;

    public SpyCommand(Storage storage) {
        super("spy");
        this.storage = storage;

        withPermission("*");
        executesPlayer(this::execute);
    }

    private void execute(Player sender, CommandArguments args) {
        storage.getUserDataContainer(sender).thenAccept(container -> {
            boolean enabled = !container.getOrDefault(SPYING_FIELD, false);
            container.set(SPYING_FIELD, enabled);

            var config = UXConfiguration.I.chat.localChat.spying;
            sender.sendMessage(Messages.INFO.create(enabled ? config.enabledSpying : config.disableSpying));
        });
    }

}
