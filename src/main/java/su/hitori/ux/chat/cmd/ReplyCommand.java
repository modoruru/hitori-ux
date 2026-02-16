package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.chat.Chat;
import su.hitori.ux.config.UXConfiguration;

public final class ReplyCommand extends CommandAPICommand {

    private final Chat chat;

    public ReplyCommand(Chat chat) {
        super("reply");
        this.chat = chat;

        withAliases("r");
        withArguments(new GreedyStringArgument("message"));
        executesPlayer(this::execute);
    }

    private void execute(Player sender, CommandArguments args) {
        String receiverName = chat.getLastDM(sender);
        Player receiver;
        if(receiverName == null || (receiver = Bukkit.getPlayer(receiverName)) == null) {
            sender.sendMessage(Messages.ERROR.create(UXConfiguration.I.chat.directMessages.noRecentMessage));
            return;
        }

        chat.sendDirectMessage(sender, receiver, args.getUnchecked("message"));
    }

}
