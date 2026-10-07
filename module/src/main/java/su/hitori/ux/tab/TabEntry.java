package su.hitori.ux.tab;

import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import su.hitori.api.Pair;
import su.hitori.api.util.Pipeline;
import su.hitori.ux.storage.DataContainer;

import java.util.*;
import java.util.function.Predicate;

public final class TabEntry implements Comparable<TabEntry> {

    final Player player;
    final DataContainer container;
    final Pipeline<Comparator<TabEntry>> sorters;
    final Predicate<Player> listedPredicate;
    final Set<TabEntry> unlisted;
    final Map<String, UUID> fakeTeams;

    boolean initialized;
    NumberFormat objectiveValue;

    @Nullable String teamName;
    @Nullable PlayerTeam team;
    boolean freshTeamName = true;
    @Nullable ClientboundSetPlayerTeamPacket teamAddPacket;
    @Nullable ClientboundPlayerInfoUpdatePacket updateDisplayNamePacket;

    TabEntry(Player player, DataContainer container, Pipeline<Comparator<TabEntry>> sorters, Predicate<Player> listedPredicate) {
        this.player = player;
        this.container = container;
        this.sorters = sorters;
        this.listedPredicate = listedPredicate;
        this.unlisted = new HashSet<>();
        this.fakeTeams = new HashMap<>();
    }

    public Player player() {
        return player;
    }

    public DataContainer container() {
        return container;
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
    public int compareTo(@NotNull TabEntry that) {
        for (Comparator<TabEntry> comparator : sorters) {
            int result = comparator.compare(this, that);
            if(result != 0) return result;
        }
        return 0;
    }

}
