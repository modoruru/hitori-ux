package su.hitori.ux.stream;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.Sound;

/**
 * Called when player receives message about someone starts the stream
 */
public class AsyncStreamStartMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final StreamInfo streamInfo;
    private final Player player;
    private final Component message;

    private Sound sound;
    private boolean cancelled;

    public AsyncStreamStartMessageEvent(StreamInfo streamInfo, Player player, Component message, Sound sound) {
        super(true);
        this.streamInfo = streamInfo;
        this.player = player;
        this.message = message;
        this.sound = sound;
    }

    public StreamInfo streamInfo() {
        return streamInfo;
    }

    public Player player() {
        return player;
    }

    public Component message() {
        return message;
    }

    public Sound sound() {
        return sound;
    }

    public void sound(Sound sound) {
        this.sound = sound;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
