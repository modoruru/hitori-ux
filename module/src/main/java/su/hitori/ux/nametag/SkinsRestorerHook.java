package su.hitori.ux.nametag;

import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.event.SkinApplyEvent;
import org.bukkit.entity.Player;
import su.hitori.api.Hitori;
import su.hitori.api.util.Task;

final class SkinsRestorerHook {

    private boolean registered;

    void register(NameTags nameTags) {
        if(registered) return;
        SkinsRestorerProvider.get().getEventBus().subscribe(
                Hitori.instance().plugin(),
                SkinApplyEvent.class,
                event -> {
                    Player player = event.getPlayer(Player.class);
                    Task.runEntity(player, () -> nameTags.forceResendPassengers(player), 3L);
                }
        );
    }

    void unregister() {
        registered = false;
    }

}
