package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.pronouns.PronounsInfluencedText;
import su.hitori.ux.pronouns.SupportedPronouns;

public final class PronounsCommand extends CommandAPICommand {

    private final UXModule uxModule;

    public PronounsCommand(UXModule uxModule) {
        super("pronouns");
        this.uxModule = uxModule;

        String[] pronounsList = new String[SupportedPronouns.values().length];
        for (int i = 0; i < pronounsList.length; i++) {
            SupportedPronouns pronouns = SupportedPronouns.values()[i];
            pronounsList[i] = pronouns.fancy;
        }

        withArguments(
                new GreedyStringArgument("pronouns")
                        .replaceSuggestions(ArgumentSuggestions.strings(pronounsList))
        );
        executesPlayer(this::execute);
    }

    private void execute(Player player, CommandArguments arguments) {
        var config = UXConfiguration.I.chat.pronouns;

        uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
            if(container == null) return;

            String rawFancyPronouns = arguments.getUnchecked("pronouns");
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
    }

}
