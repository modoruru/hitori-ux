package su.hitori.ux.chat.channel;

import net.kyori.adventure.key.Keyed;
import org.bukkit.entity.Player;
import su.hitori.ux.storage.api.DataContainer;

import java.util.Set;

public interface ChatChannel extends Keyed {

    char prefixSymbol();

    boolean isPrivate();

    Set<Player> resolveReceivers(Player sender, DataContainer senderContainer);

    String format();

    static ChatChannel createLocal() {
        return new LocalChatChannel();
    }

    static ChatChannel createGlobal() {
        return new GlobalChatChannel();
    }

}
