package su.hitori.ux.chat.channel;

import net.kyori.adventure.key.Keyed;
import org.bukkit.entity.Player;
import su.hitori.api.util.Either;
import su.hitori.ux.storage.DataContainer;

import java.util.Set;

public interface ChatChannel extends Keyed {

    char prefixSymbol();

    boolean isPrivate();

    /**
     * @return a set of receivers or an error that will be sent to the player explaining why the message wouldn't be sent
     */
    Either<Set<Player>, String> resolveReceivers(Player sender, DataContainer senderContainer);

    String format();

    static ChatChannel createLocal() {
        return new LocalChatChannel();
    }

    static ChatChannel createGlobal() {
        return new GlobalChatChannel();
    }

}
