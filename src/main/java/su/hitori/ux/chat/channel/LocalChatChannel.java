package su.hitori.ux.chat.channel;

import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.hitori.api.util.Either;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataContainer;

import java.util.HashSet;
import java.util.Set;

final class LocalChatChannel implements ChatChannel {

    LocalChatChannel() {
    }

    @Override
    public char prefixSymbol() {
        return '\0';
    }

    @Override
    public boolean isPrivate() {
        return true;
    }

    @Override
    public Either<Set<Player>, String> resolveReceivers(Player sender, DataContainer senderContainer) {
        var localChatConfig = UXConfiguration.I.chat.localChat;

        // find message receivers
        Location senderLocation = sender.getLocation();
        Set<Player> receivers = new HashSet<>(Bukkit.getOnlinePlayers()); // use optimized set
        double radius = localChatConfig.radius * localChatConfig.radius;
        receivers.removeIf(
                player -> {
                    Location location = player.getLocation();
                    if(location.getWorld() != senderLocation.getWorld()) return true;
                    return player.getLocation().distanceSquared(senderLocation) > radius;
                }
        );

        return Either.ofFirst(receivers);
    }

    @Override
    public String format() {
        return UXConfiguration.I.chat.structure.local;
    }

    @Override
    public @NotNull Key key() {
        return Key.key("hitori", "local");
    }

}
