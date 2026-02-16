package su.hitori.ux.chat.event;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.storage.api.DataContainer;

public class AsyncPlayerOnlineMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final DataContainer container;
    private final Component message;
    private final boolean nowOnline;

    private boolean cancelled;

    public AsyncPlayerOnlineMessageEvent(Player player, DataContainer container, Component message, boolean nowOnline) {
        super(true);
        this.player = player;
        this.container = container;
        this.message = message;
        this.nowOnline = nowOnline;
    }

    public Player player() {
        return player;
    }

    public DataContainer container() {
        return container;
    }

    public Component message() {
        return message;
    }

    public boolean nowOnline() {
        return nowOnline;
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
