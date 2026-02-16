package su.hitori.ux.nametag;

import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import su.hitori.api.nms.NMSUtil;
import su.hitori.api.util.Text;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholders;

final class NameTagEntity {

    private static final DynamicPlaceholder<Player> PLAYER_NAME_PLACEHOLDER = DynamicPlaceholder.create("player_name", Player::getName);

    final NameTags nameTags;
    final Player player;
    final ServerPlayer serverPlayer;
    TextDisplay textDisplay;

    boolean initialized;

    NameTagEntity(NameTags nameTags, Player player, ServerPlayer serverPlayer, TextDisplay textDisplay) {
        this.nameTags = nameTags;
        this.player = player;
        this.serverPlayer = serverPlayer;
        this.textDisplay = textDisplay;
    }

    private static TextDisplay createAndSetup(Player player) {
        Location location = player.getLocation();
        TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class);
        for (Entity passenger : player.getPassengers()) {
            player.removePassenger(passenger);
        }
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setSeeThrough(true);
        textDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0.2f, 0),
                new Quaternionf(),
                new Vector3f(1f),
                new Quaternionf()
        ));
        if(!UXConfiguration.I.nameTags.background) {
            textDisplay.setBackgroundColor(Color.fromARGB(0));
            textDisplay.setShadowed(true);
        }
        player.addPassenger(textDisplay);
        return textDisplay;
    }

    static NameTagEntity create(NameTags nameTags, Player player) {
        return new NameTagEntity(nameTags, player, NMSUtil.asNMS(player), createAndSetup(player));
    }

    void resendPassengers() {
        serverPlayer.connection.send(new ClientboundSetPassengersPacket(serverPlayer));
    }

    void update(PlayerTeam playerTeam, boolean updateTeamData) {
        if(!initialized) {
            playerTeam.getPlayers().add(player.getName());
            serverPlayer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));

            initialized = true;
        }
        else if (updateTeamData) {
            serverPlayer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(playerTeam));
            serverPlayer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        }

        boolean lying = nameTags.isLying(player);

        if(!lying && (player.isInvisible() || player.getPotionEffect(PotionEffectType.INVISIBILITY) != null || player.getGameMode() == GameMode.SPECTATOR || player.isDead())) {
            if(textDisplay != null) {
                textDisplay.remove();
                textDisplay = null;
            }
            return;
        }

        if(textDisplay == null)
            textDisplay = createAndSetup(player);

        if(lying) {
            player.removePassenger(textDisplay);
            textDisplay.teleport(player.getLocation().add(0, 1, 0));
        }
        else if(player.getPassengers().isEmpty())
            player.addPassenger(textDisplay);

        Location location = player.getLocation();
        if(location.getWorld() != textDisplay.getWorld()) {
            textDisplay.teleport(location);
            if(!lying) player.addPassenger(textDisplay);
        }

        textDisplay.text(Text.create(Placeholders.resolveDynamic(
                UXConfiguration.I.nameTags.format,
                player,
                PLAYER_NAME_PLACEHOLDER
        )));
    }

    void remove() {
        player.removePassenger(textDisplay);
        textDisplay.remove();
    }

}
