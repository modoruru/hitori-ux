package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataField;

public final class SpyCommand extends CommandAPICommand {

    public static final DataField<Boolean> SPYING_FIELD = DataField.createBoolean("spying");

    private final UXModule uxModule;

    public SpyCommand(UXModule uxModule) {
        super("spy");
        this.uxModule = uxModule;

        withPermission("*");
        executesPlayer(this::execute);
    }

    private void execute(Player sender, CommandArguments args) {
        uxModule.storage().getUserDataContainer(sender).thenAccept(container -> {
            boolean enabled = !container.getOrDefault(SPYING_FIELD, false);
            container.set(SPYING_FIELD, enabled);

            var config = UXConfiguration.I.chat.localChat.spying;
            sender.sendMessage(Messages.INFO.create(enabled ? config.enabledSpying : config.disableSpying));
        });
    }

}
