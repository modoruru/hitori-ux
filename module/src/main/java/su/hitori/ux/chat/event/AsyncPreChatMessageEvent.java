package su.hitori.ux.chat.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.chat.channel.ChatChannel;
import su.hitori.ux.storage.DataContainer;

public class AsyncPreChatMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final DataContainer sender;
    private final String originalMessage;
    private final ChatChannel chatChannel;

    private String formattedMessage;
    private boolean cancelled;

    public AsyncPreChatMessageEvent(DataContainer sender, String originalMessage, ChatChannel chatChannel, String formattedMessage) {
        super(true);
        this.sender = sender;
        this.originalMessage = originalMessage;
        this.chatChannel = chatChannel;
        this.formattedMessage = formattedMessage;
    }

    public DataContainer sender() {
        return sender;
    }

    public String originalMessage() {
        return originalMessage;
    }

    // formatted user input
    public String formattedMessage() {
        return formattedMessage;
    }

    public void formattedMessage(@NotNull String formattedMessage) {
        this.formattedMessage = formattedMessage;
    }

    public ChatChannel chatChannel() {
        return chatChannel;
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

    @SuppressWarnings("unused") // fuck bukkit events system
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
