package su.hitori.ux.tab;

import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.hitori.api.Pair;
import su.hitori.api.util.Pipeline;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

final class TabEntry implements Comparable<TabEntry> {

    final Player player;
    final Pipeline<Comparator<Player>> sorters;
    final Predicate<Player> listedPredicate;
    final Set<TabEntry> unlisted;
    final Set<PlayerTeam> fakeTeams;

    boolean initialized;
    NumberFormat objectiveValue;

    TabEntry(Player player, Pipeline<Comparator<Player>> sorters, Predicate<Player> listedPredicate) {
        this.player = player;
        this.sorters = sorters;
        this.listedPredicate = listedPredicate;
        this.unlisted = new HashSet<>();
        this.fakeTeams = new HashSet<>();
    }

    /**
     * @return should update & current state
     */
    Pair<Boolean, Boolean> isListed(TabEntry other) {
        if(other == this) return Pair.of(false, true);
        boolean listed = !this.unlisted.contains(other);

        if(listedPredicate.test(player) == listed) return Pair.of(false, listed);

        if(listed) unlisted.remove(other);
        else unlisted.add(other);

        return Pair.of(true, listed);
    }

    @Override
    public int compareTo(@NotNull TabEntry o) {
        for (Comparator<Player> comparator : sorters) {
            int result = comparator.compare(player, o.player);
            if(result != 0) return result;
        }
        return 0;
    }

}
