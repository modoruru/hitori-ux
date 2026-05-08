package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import su.hitori.ux.chat.Chat;

public final class DirectMessageCommand extends CommandAPICommand {

    public DirectMessageCommand(Chat chat) {
        super("msg");
        withAliases("m", "w", "tell");
        withArguments(new EntitySelectorArgument.OnePlayer("receiver"), new GreedyStringArgument("message"));
        executesPlayer((player, args) -> {
            chat.sendDirectMessage(player, args.getUnchecked("receiver"), args.getUnchecked("message"));
        });
    }

}
