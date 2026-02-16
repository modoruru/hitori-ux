package su.hitori.ux.chat.channel;

import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.api.DataContainer;

import java.util.HashSet;
import java.util.Set;

final class GlobalChatChannel implements ChatChannel {

    GlobalChatChannel() {}

    @Override
    public char prefixSymbol() {
        return '!';
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public Set<Player> resolveReceivers(Player sender, DataContainer senderContainer) {
        return new HashSet<>(Bukkit.getOnlinePlayers());
    }

    @Override
    public String format() {
        return UXConfiguration.I.chat.structure.global;
    }

    @Override
    public @NotNull Key key() {
        return Key.key("hitori", "global");
    }

}
