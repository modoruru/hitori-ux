package su.hitori.ux.chat;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import su.hitori.ux.chat.channel.ChatChannel;
import su.hitori.ux.storage.DataContainer;

/**
 * Pre-processed message is the one that requires local Player to process.
 * You can create one and invoke {@link Chat#sendPreProcessed(PreProcessedMessage)} but module will not modify it in any way.
 *
 * @param sender Can be null if the message wasn't sent locally.
 */
public record PreProcessedMessage(@Nullable Player sender, DataContainer senderContainer, String originalContent, long creationTime, ChatChannel chatChannel, String preProcessedContent) {
}
