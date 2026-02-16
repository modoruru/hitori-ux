package su.hitori.ux.storage;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StorageListener implements Listener {

    private final Storage storage;
    private final boolean resourcepackExists;
    private final Set<Player> previouslyLoaded;

    public StorageListener(Storage storage, boolean resourcepackExists) {
        this.storage = storage;
        this.resourcepackExists = resourcepackExists;
        this.previouslyLoaded = new HashSet<>();
    }

    @EventHandler
    private void onPlayerLogin(PlayerServerFullCheckEvent event) {
        PlayerProfile playerProfile = event.getPlayerProfile();

        UUID uuid = playerProfile.getId();
        String name = playerProfile.getName();
        assert uuid != null && name != null;

        storage.createIdentifierIfAbsent(uuid, name);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        if(!resourcepackExists) storage.syncPlayer(event.getPlayer(), true);
    }

    @EventHandler
    private void onPlayerResourcepackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        if(!resourcepackExists || event.getStatus() != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED || previouslyLoaded.contains(player)) return;
        previouslyLoaded.add(player);
        storage.syncPlayer(player, true);
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        previouslyLoaded.remove(player);
        storage.quit(player);
    }

}
