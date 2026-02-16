package su.hitori.ux.permission;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

public enum DefaultPermission implements Permission {

    STREAM_TOGGLE,
    CHAT_FORMATTING;

    private final Key key;

    DefaultPermission() {
        this.key = Key.key("hitori", name().toLowerCase());
    }

    DefaultPermission(String id) {
        this.key = Key.key("hitori", id);
    }

    DefaultPermission(DefaultPermission parent, String id) {
        this(parent.key().value() + '.' + id);
    }

    @Override
    public @NotNull Key key() {
        return key;
    }

}
