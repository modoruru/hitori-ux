package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import su.hitori.ux.chat.Chat;

public final class HelloCommand {

    private HelloCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(Chat chat) {
        return Commands.literal("hello")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            PlayerSelectorArgumentResolver resolver = context.getArgument("player", PlayerSelectorArgumentResolver.class);
                            chat.sendHello((Player) context.getSource().getSender(), resolver.resolve(context.getSource()).getFirst());
                            return 1;
                        }))
                .build();
    }

}
