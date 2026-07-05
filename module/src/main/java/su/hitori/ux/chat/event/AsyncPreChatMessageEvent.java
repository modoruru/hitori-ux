package su.hitori.ux.chat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.hitori.ux.chat.channel.ChatChannel;
import su.hitori.ux.storage.DataContainer;

public class AsyncPreChatMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public final Player sender;
    public final DataContainer senderContainer;
    public final String originalContent;
    public final long creationTime;
    public final ChatChannel chatChannel;
    public String preProcessedContent;

    private boolean cancelled;

    public AsyncPreChatMessageEvent(@Nullable Player sender, DataContainer senderContainer, String originalContent, long creationTime, ChatChannel chatChannel, String preProcessedContent) {
        super(true);
        this.sender = sender;
        this.senderContainer = senderContainer;
        this.originalContent = originalContent;
        this.creationTime = creationTime;
        this.chatChannel = chatChannel;
        this.preProcessedContent = preProcessedContent;
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
