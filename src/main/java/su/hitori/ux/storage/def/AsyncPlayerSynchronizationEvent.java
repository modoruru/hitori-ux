package su.hitori.ux.storage.def;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.storage.DataContainer;

public class AsyncPlayerSynchronizationEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final DataContainer container;

    public AsyncPlayerSynchronizationEvent(Player player, DataContainer container) {
        super(true);
        this.player = player;
        this.container = container;
    }

    public Player player() {
        return player;
    }

    public DataContainer container() {
        return container;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
