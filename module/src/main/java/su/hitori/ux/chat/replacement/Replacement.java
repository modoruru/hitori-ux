package su.hitori.ux.chat.replacement;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import org.bukkit.entity.Player;
import su.hitori.api.registry.Registry;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Replacement extends Keyed {

    String word();

    String format();

    Collection<DynamicPlaceholder<Player>> placeholders();

    static Replacement createDefault(Key key, String word, String format, Set<DynamicPlaceholder<Player>> placeholders) {
        return new ReplacementImpl(key, word, format, placeholders);
    }

    static void fillWithReplacements(Registry<Replacement> replacementRegistry, Player player, StringBuilder input) {
        for (Replacement replacement : replacementRegistry.elements()) {
            Pattern pattern = Pattern.compile("\\b" + replacement.word() + "\\b");
            Matcher matcher = pattern.matcher(input);

            int offset = 0;
            String formatted = null;
            while (matcher.find()) {
                if(formatted == null) {
                    formatted = Placeholders.resolve(
                            UXConfiguration.I.chat.replacements.allFormat,
                            Placeholder.create(
                                    "formatted_replacement",
                                    () -> Placeholders.resolveDynamic(
                                            replacement.format(),
                                            player,
                                            replacement.placeholders().toArray(new DynamicPlaceholder[0])
                                    )
                            )
                    );
                }

                input.replace(matcher.start() + offset, matcher.end() + offset, formatted);
                offset += formatted.length() - (matcher.end() - matcher.start());
                matcher.reset(input.substring(offset));
            }
        }
    }

}
