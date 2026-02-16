package su.hitori.ux.chat.event;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.Sound;

public class AsyncFirstVisitMessageEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Component message;
    private Sound sound;

    public AsyncFirstVisitMessageEvent(Player player, Component message, Sound sound) {
        super(true);
        this.player = player;
        this.message = message;
        this.sound = sound;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
