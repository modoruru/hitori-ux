package su.hitori.ux.chat.cmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.pronouns.PronounsInfluencedText;
import su.hitori.ux.pronouns.SupportedPronouns;

public final class PronounsCommand {

    private PronounsCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(UXModule uxModule) {
        String[] pronounsList = new String[SupportedPronouns.values().length];
        for (int i = 0; i < pronounsList.length; i++) {
            SupportedPronouns pronouns = SupportedPronouns.values()[i];
            pronounsList[i] = pronouns.fancy;
        }

        return Commands.literal("pronouns")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.argument("pronouns", StringArgumentType.greedyString())
                        .suggests((_, builder) -> {
                            for (String pronouns : pronounsList) {
                                builder.suggest(pronouns);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> execute(uxModule, context)))
                .build();
    }

    private static int execute(UXModule uxModule, CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getSender();
        var config = UXConfiguration.I.chat.pronouns;

        uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
            if(container == null) return;

            String rawFancyPronouns = context.getArgument("pronouns", String.class);
            assert rawFancyPronouns != null;

            SupportedPronouns parsed = null;
            for (SupportedPronouns pronouns : SupportedPronouns.values()) {
                if(pronouns.fancy.equalsIgnoreCase(rawFancyPronouns)) {
                    parsed = pronouns;
                    break;
                }
            }

            Placeholder pronounsPlaceholder = Placeholder.createFinal("pronouns", rawFancyPronouns.toLowerCase());

            if(parsed == null) {
                player.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                        config.noSuchPronouns,
                        pronounsPlaceholder
                )));
                return;
            }

            SupportedPronouns current = container.getOrDefault(PronounsInfluencedText.PRONOUNS_FIELD, PronounsInfluencedText.DEFAULT);
            if(current == parsed) {
                player.sendMessage(Messages.ERROR.create(Placeholders.resolve(
                        config.alreadySet,
                        pronounsPlaceholder
                )));
                return;
            }

            container.set(PronounsInfluencedText.PRONOUNS_FIELD, parsed);
            player.sendMessage(Messages.INFO.create(Placeholders.resolve(
                    config.nowSet,
                    pronounsPlaceholder
            )));
        });

        return 1;
    }

}
