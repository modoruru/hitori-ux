package su.hitori.ux.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.Sound;

/**
 * Called when player receives reminder about planned event
 */
public class AsyncEventReminderEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final su.hitori.ux.event.Event event;
    private final Player player;

    private Sound sound;
    private boolean cancelled;

    public AsyncEventReminderEvent(@NotNull su.hitori.ux.event.Event event, @NotNull Player player, Sound sound) {
        super(true);
        this.event = event;
        this.player = player;
        this.sound = sound;
    }

    public su.hitori.ux.event.Event event() {
        return event;
    }

    public Player player() {
        return player;
    }

    public Sound sound() {
        return sound;
    }

    public void sound(Sound sound) {
        this.sound = sound;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
