package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.entity.Player;
import su.hitori.ux.chat.Chat;
import su.hitori.ux.chat.SharedInventoryContainer;

import java.util.UUID;

public final class OpenSharedInventoryCommand {

    private OpenSharedInventoryCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(Chat chat) {
        return Commands.literal("sharedinventory")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.argument("uuid", ArgumentTypes.uuid())
                        .executes(context -> openSharedInventory(chat, context)))
                .build();
    }

    private static int openSharedInventory(Chat chat, CommandContext<CommandSourceStack> context) {
        SharedInventoryContainer sharedInventory = chat.getSharedInventory(context.getArgument("uuid", UUID.class));
        if(sharedInventory == null) {
            // todo: add error message
            return 0;
        }

        ((Player) context.getSource().getSender()).openInventory(sharedInventory.getInventory());
        return 1;
    }

}
