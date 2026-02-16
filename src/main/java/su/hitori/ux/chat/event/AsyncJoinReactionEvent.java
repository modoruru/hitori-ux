package su.hitori.ux.chat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AsyncJoinReactionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player, joined;
    private String helloFormat;
    private boolean cancelled;

    public AsyncJoinReactionEvent(Player player, Player joined, String helloFormat) {
        super(true);
        this.player = player;
        this.joined = joined;
        this.helloFormat = helloFormat;
    }

    public Player player() {
        return player;
    }

    public Player joined() {
        return joined;
    }

    public String helloFormat() {
        return helloFormat;
    }

    public void helloFormat(String helloFormat) {
        this.helloFormat = helloFormat;
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
        cancelled = cancel;
    }

}
