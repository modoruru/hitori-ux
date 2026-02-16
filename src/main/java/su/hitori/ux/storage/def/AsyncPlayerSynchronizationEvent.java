package su.hitori.ux.storage.def;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.storage.Identifier;

public class AsyncPlayerSynchronizationEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Identifier identifier;

    public AsyncPlayerSynchronizationEvent(Player player, Identifier identifier) {
        super(true);
        this.player = player;
        this.identifier = identifier;
    }

    public Player player() {
        return player;
    }

    public Identifier identifier() {
        return identifier;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
