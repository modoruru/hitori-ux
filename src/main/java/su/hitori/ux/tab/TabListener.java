package su.hitori.ux.tab;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TabListener implements Listener {

    private final Tab tab;

    public TabListener(Tab tab) {
        this.tab = tab;
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        tab.addPlayer(event.getPlayer());
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        tab.removePlayer(event.getPlayer());
    }

}
