package su.hitori.ux.tab;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import su.hitori.ux.storage.def.AsyncPlayerSynchronizationEvent;

public final class TabListener implements Listener {

    private final Tab tab;

    public TabListener(Tab tab) {
        this.tab = tab;
    }

    @EventHandler
    private void onPlayerJoin(AsyncPlayerSynchronizationEvent event) {
        tab.addPlayer(event.player(), event.container());
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        tab.removePlayer(event.getPlayer());
    }

}
