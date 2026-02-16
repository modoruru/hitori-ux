package su.hitori.ux.permission;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.util.TriState;
import org.bukkit.permissions.Permissible;

public interface Permission extends Keyed {

    default boolean hasPermission(Permissible player) {
        return player.hasPermission(key().asString());
    }

    default TriState permissionValue(Permissible player) {
        return player.permissionValue(key().asString());
    }

    default String asString() {
        return key().asString();
    }

    static Permission create(Key key) {
        return new SimplePermission(key);
    }


}
