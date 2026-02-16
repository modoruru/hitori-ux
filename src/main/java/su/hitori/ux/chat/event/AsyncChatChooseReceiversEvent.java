package su.hitori.ux.chat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.chat.channel.ChatChannel;

import java.util.Set;

public class AsyncChatChooseReceiversEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player sender;
    private final ChatChannel chatChannel;
    private final Set<Player> receivers;

    public AsyncChatChooseReceiversEvent(Player sender, ChatChannel chatChannel, Set<Player> receivers) {
        super(true);
        this.sender = sender;
        this.chatChannel = chatChannel;
        this.receivers = receivers;
    }

    public Player sender() {
        return sender;
    }

    public ChatChannel chatChannel() {
        return chatChannel;
    }

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
