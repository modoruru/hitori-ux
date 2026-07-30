package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataField;

public final class SpyCommand {

    public static final DataField<Boolean> SPYING_FIELD = DataField.createBoolean("spying");

    private SpyCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(UXModule uxModule) {
        return Commands.literal("spy")
                .requires(source -> source.getSender().hasPermission("*") && source.getSender() instanceof Player)
                .executes(context -> execute(uxModule, context))
                .build();
    }

    private static int execute(UXModule uxModule, CommandContext<CommandSourceStack> source) {
        Player player = (Player) source.getSource().getSender();
        uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
            if(container == null) return;

            boolean enabled = !container.getOrDefault(SPYING_FIELD, false);
            container.set(SPYING_FIELD, enabled);

            var config = UXConfiguration.I.chat.localChat.spying;
            player.sendMessage(Messages.INFO.create(enabled ? config.enabledSpying : config.disableSpying));
        });

        return 1;
    }

}
