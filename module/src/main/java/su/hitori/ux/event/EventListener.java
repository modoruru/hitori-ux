package su.hitori.ux.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class EventListener implements Listener {

    private final Events events;

    public EventListener(Events events) {
        this.events = events;
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        events.getNonHiddenActiveEvents(player).thenAccept(events -> {
            if(events.isEmpty()) return;

            if(events.size() == 1) {
                this.events.showReminder(player, events.getFirst());
                return;
            }

            this.events.showEventsList(player, events);
        });
    }

}
