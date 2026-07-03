package su.hitori.ux.nametag;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import su.hitori.api.util.Task;

public final class NameTagsListener implements Listener {

    private final NameTags nameTags;

    public NameTagsListener(NameTags nameTags) {
        this.nameTags = nameTags;
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        nameTags.track(event.getPlayer());
    }

    @EventHandler
    private void onPlayerWorld(PlayerQuitEvent event) {
        nameTags.untrack(event.getPlayer());
    }

    @EventHandler
    private void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Task.runEntity(player, () -> {
            if(player.getGameMode() == GameMode.SPECTATOR || player.getPreviousGameMode() == GameMode.SPECTATOR)
                nameTags.forceUpdate(player);
        }, 1L);
    }

    @EventHandler
    private void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        nameTags.forceUpdate(event.getPlayer());
    }

    @EventHandler
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        nameTags.forceResendPassengers(event.getPlayer());
    }

}
