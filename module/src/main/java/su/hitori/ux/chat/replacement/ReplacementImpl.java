package su.hitori.ux.chat.replacement;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.hitori.ux.placeholder.DynamicPlaceholder;

import java.util.Collection;
import java.util.Set;

final class ReplacementImpl implements Replacement {

    private final Key key;
    private final String word, format;
    private final Set<DynamicPlaceholder<Player>> placeholders;

    ReplacementImpl(Key key, String word, String format, Collection<DynamicPlaceholder<Player>> placeholders) {
        this.key = key;
        this.word = word;
        this.format = format;
        this.placeholders = Set.copyOf(placeholders);
    }

    @Override
    public String word() {
        return word;
    }

    @Override
    public String format() {
        return format;
    }

    @Override
    public Collection<DynamicPlaceholder<Player>> placeholders() {
        return placeholders;
    }

    @Override
    public @NotNull Key key() {
        return key;
    }

}
