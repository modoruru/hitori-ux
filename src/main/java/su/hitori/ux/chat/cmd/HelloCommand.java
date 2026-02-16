package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.entity.Player;
import su.hitori.ux.chat.Chat;

public final class HelloCommand extends CommandAPICommand {

    private final Chat chat;

    public HelloCommand(Chat chat) {
        super("hello");
        this.chat = chat;

        withArguments(new EntitySelectorArgument.OnePlayer("player"));
        executesPlayer(this::execute);
    }

    private void execute(Player sender, CommandArguments args) {
        chat.sendHello(sender, (Player) args.get("player"));
    }

}
