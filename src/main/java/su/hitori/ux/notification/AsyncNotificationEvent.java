package su.hitori.ux.notification;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.Sound;

public class AsyncNotificationEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final NotificationType type;

    private String text;
    private Sound sound;

    private boolean cancelled;

    public AsyncNotificationEvent(Player player, NotificationType type, String text, Sound sound) {
        super(true);
        this.player = player;
        this.type = type;
        this.text = text;
        this.sound = sound;
    }

    public Player player() {
        return player;
    }

    public NotificationType type() {
        return type;
    }

    public String text() {
        return text;
    }

    public void text(String text) {
        this.text = text;
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

    @SuppressWarnings("unused") // fuck bukkit events system
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

}
