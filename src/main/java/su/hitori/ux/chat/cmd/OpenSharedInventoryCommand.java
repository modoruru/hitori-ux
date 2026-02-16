package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.UUIDArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.entity.Player;
import su.hitori.ux.chat.Chat;
import su.hitori.ux.chat.SharedInventoryContainer;

import java.util.UUID;

public final class OpenSharedInventoryCommand extends CommandAPICommand {

    private final Chat chat;

    public OpenSharedInventoryCommand(Chat chat) {
        super("sharedinventory");
        this.chat = chat;
        withArguments(new UUIDArgument("uuid")).executesPlayer(this::openSharedInventory);
    }

    private void openSharedInventory(Player sender, CommandArguments args) {
        SharedInventoryContainer sharedInventory = chat.getSharedInventory((UUID) args.get("uuid"));
        if(sharedInventory == null) {
            // todo: add error message
            return;
        }

        sender.openInventory(sharedInventory.getInventory());
    }

}
