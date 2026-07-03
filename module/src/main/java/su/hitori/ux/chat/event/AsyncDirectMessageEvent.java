package su.hitori.ux.chat.event;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.Sound;

public class AsyncDirectMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player sender, receiver;
    private final String originalMessage;
    private Component receiverResult, senderResult;
    private Sound receiveSound;

    private boolean cancelled;

    public AsyncDirectMessageEvent(Player sender, Player receiver, String originalMessage, Component receiverResult, Component senderResult, Sound receiveSound) {
        super(true);
        this.sender = sender;
        this.receiver = receiver;
        this.originalMessage = originalMessage;
        this.receiverResult = receiverResult;
        this.senderResult = senderResult;
        this.receiveSound = receiveSound;
    }

    public Player sender() {
        return sender;
    }

    public Player receiver() {
        return receiver;
    }

    public String originalMessage() {
        return originalMessage;
    }

    public Component receiverResult() {
        return receiverResult;
    }

    public void receiverResult(@NotNull Component receiverResult) {
        this.receiverResult = receiverResult;
    }

    public Component senderResult() {
        return senderResult;
    }

    public void senderResult(@NotNull Component senderResult) {
        this.senderResult = senderResult;
    }

    public Sound receiveSound() {
        return receiveSound;
    }

    public void receiveSound(Sound receiveSound) {
        this.receiveSound = receiveSound;
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
