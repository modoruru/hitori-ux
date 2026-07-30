package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import su.hitori.ux.chat.Chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DirectMessageCommand {

    private DirectMessageCommand() {}

    public static Collection<LiteralCommandNode<CommandSourceStack>> bootstrap(Chat chat) {
        LiteralCommandNode<CommandSourceStack> command = Commands.literal("msg")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.argument("receiver", ArgumentTypes.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    chat.sendDirectMessage(
                                            (Player) context.getSource().getSender(),
                                            context.getArgument("receiver", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst(),
                                            context.getArgument("message", String.class)
                                    );
                                    return 1;
                                })))
                .build();

        List<LiteralCommandNode<CommandSourceStack>> nodes = new ArrayList<>();
        nodes.add(command);

        for (String literal : List.of("m", "w", "tell")) {
            nodes.add(Commands.literal(literal).redirect(command).build());
        }

        return nodes;
    }

}
