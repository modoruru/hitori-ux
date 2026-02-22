package su.hitori.ux.advertisement;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when player receives an advertising notification
 */
public class AsyncAdvertisementEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Component advertisement;

    private boolean cancelled;

    protected AsyncAdvertisementEvent(Player player, Component advertisement) {
        super(true);
        this.player = player;
        this.advertisement = advertisement;
    }

    /**
     * Receiver of advertisement
     */
    public Player player() {
        return player;
    }

    /**
     * Advertisement message
     */
    public Component advertisement() {
        return advertisement;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
