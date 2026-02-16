package su.hitori.ux.chat.cmd;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.ux.GenderInfluencedText;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;

public final class GenderCommand extends CommandAPICommand {

    private final UXModule uxModule;

    public GenderCommand(UXModule uxModule) {
        super("gender");
        this.uxModule = uxModule;

        withSubcommands(
                new CommandAPICommand("man")
                        .executesPlayer(this::becameMan),
                new CommandAPICommand("woman")
                        .executesPlayer(this::becameWoman)
        );
    }

    private void becameMan(Player sender, CommandArguments args) {
        setGender(sender, true);
    }

    private void becameWoman(Player sender, CommandArguments args) {
        setGender(sender, false);
    }

    private void setGender(Player player, boolean man) {
        var config = UXConfiguration.I.chat.gender;

        uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
            if("man".equals(container.getOrDefault(GenderInfluencedText.GENDER_FIELD, "man")) == man) {
                player.sendMessage(Messages.ERROR.create(man ? config.already_man : config.already_woman));
                return;
            }

            container.set(GenderInfluencedText.GENDER_FIELD, man ? "man" : "woman");

            player.sendMessage(Messages.INFO.create(man ? config.now_man : config.now_woman));
        });
    }

}
