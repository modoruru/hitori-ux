package su.hitori.ux.chat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.chat.channel.ChatChannel;
import su.hitori.ux.storage.DataContainer;

import java.util.Set;

/**
 * Called when chat resolving receivers for chat message.
 */
public class AsyncChatChooseReceiversEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final DataContainer sender;
    private final ChatChannel chatChannel;
    private final Set<Player> receivers;

    public AsyncChatChooseReceiversEvent(DataContainer sender, ChatChannel chatChannel, Set<Player> receivers) {
        super(true);
        this.sender = sender;
        this.chatChannel = chatChannel;
        this.receivers = receivers;
    }

    /**
     * Message sender
     */
    public DataContainer sender() {
        return sender;
    }

    /**
     * Chat channel that is getting message
     */
    public ChatChannel chatChannel() {
        return chatChannel;
    }

    /**
     * Mutable set containing message receivers
     */
    public Set<Player> receivers() {
        return receivers;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
