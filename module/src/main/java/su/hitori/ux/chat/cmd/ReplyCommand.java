package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.chat.Chat;
import su.hitori.ux.config.UXConfiguration;

import java.util.Collection;
import java.util.List;

public final class ReplyCommand {

    private ReplyCommand() {}

    public static Collection<LiteralCommandNode<CommandSourceStack>> bootstrap(Chat chat) {
        LiteralCommandNode<CommandSourceStack> command = Commands.literal("reply")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            Player sender = (Player) context.getSource().getSender();
                            String receiverName = chat.getLastDM(sender);
                            Player receiver;
                            if(receiverName == null || (receiver = Bukkit.getPlayer(receiverName)) == null) {
                                sender.sendMessage(Messages.ERROR.create(UXConfiguration.I.chat.directMessages.noRecentMessage));
                                return 0;
                            }

                            chat.sendDirectMessage(sender, receiver, context.getArgument("message", String.class));
                            return 1;
                        }))
                .build();

        return List.of(
                command,
                Commands.literal("reply").redirect(command).build()
        );
    }

}
