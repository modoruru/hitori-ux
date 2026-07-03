package su.hitori.ux.storage.remote;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RemoteStorageListener implements Listener {

    private final RemoteStorage remoteStorage;

    public RemoteStorageListener(RemoteStorage remoteStorage) {
        this.remoteStorage = remoteStorage;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        remoteStorage.syncPlayer(event.getPlayer());
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        remoteStorage.quit(player);
    }

}
