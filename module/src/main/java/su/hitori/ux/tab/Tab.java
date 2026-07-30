package su.hitori.ux.tab;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.key.Key;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import su.hitori.api.Hitori;
import su.hitori.api.Pair;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.nms.PacketBundleBuilder;
import su.hitori.api.util.LoggerUtil;
import su.hitori.api.util.Pipeline;
import su.hitori.api.util.Task;
import su.hitori.api.util.Text;
import su.hitori.ux.UXModule;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.DataContainer;

import java.util.*;
import java.util.logging.Logger;

import static su.hitori.api.nms.NMSUtil.asNMS;

public final class Tab {

    private static final Logger LOGGER = LoggerFactory.instance().create(Tab.class);
    private static final @Nullable FoliaOnlyAPI FOLIA_ONLY_API;

    private static final String OBJECTIVE_NAME = "hitori_tab";
    private static final DynamicPlaceholder<Player>[] OBJECTIVE_PLACEHOLDERS = new DynamicPlaceholder[]{
            DynamicPlaceholder.create("player_name", Player::getName),
            DynamicPlaceholder.create("ping", Player::getPing)
    };

    private static final DynamicPlaceholder<Player>[] HEADER_FOOTER_PLACEHOLDERS;

    static {
        if(Hitori.instance().serverCoreInfo().isFolia()) FOLIA_ONLY_API = new FoliaOnlyAPI();
        else FOLIA_ONLY_API = null;

        HEADER_FOOTER_PLACEHOLDERS = new DynamicPlaceholder[]{
                DynamicPlaceholder.<Player>create("tps", player -> String.format(
                        "%.1f",
                        (FOLIA_ONLY_API == null ? Bukkit.getServer().getTPS() : FOLIA_ONLY_API.getRegionTPS(player.getLocation()))[0]
                )),
                DynamicPlaceholder.<Player>create("mspt", player -> String.format(
                        "%.1f",
                        FOLIA_ONLY_API == null ? Bukkit.getServer().getAverageTickTime() : FOLIA_ONLY_API.getRegionMSPT(player.getLocation())
                )),
                DynamicPlaceholder.<Player>create("online", _ -> Bukkit.getOnlinePlayers().size()),
                DynamicPlaceholder.create("ping", Player::getPing),
                DynamicPlaceholder.create("player_name", Player::getName)
        };
    }

    private final UXModule uxModule;
    private final Scoreboard scoreboard;

    private final Map<Player, TabEntry> tabEntries;
    private final Pipeline<Comparator<TabEntry>> sorters;
    private final UXConfiguration.Tab configuration;

    private Task task;

    public Tab(UXModule uxModule) {
        this.uxModule = uxModule;
        this.scoreboard = new Scoreboard();

        this.tabEntries = new HashMap<>();
        this.sorters = new Pipeline<>();
        this.configuration = UXConfiguration.I.tab;

        sorters.addLast(
                Key.key("ux", "is_op"),
                (first, second) -> -Boolean.compare(first.player.isOp(), second.player.isOp())
        );
        sorters.addLast(
                Key.key("ux", "alphabetical"),
                (first, second) -> first.player.getName().compareToIgnoreCase(second.player.getName())
        );

        scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.empty(),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
        );
    }

    public Pipeline<Comparator<TabEntry>> sortingPipeline() {
        return sorters;
    }

    // logic methods
    public void start() {
        if(task != null || !UXConfiguration.I.tab.enabled) return;

        task = Task.runTaskTimerAsync(
                this::updateAsync,
                1L,
                Math.max(1, configuration.updateIntervalSeconds) * 20L
        );

        Bukkit.getOnlinePlayers().forEach(this::addPlayer);
    }

    public void stop() {
        if(task == null) return;

        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        assert objective != null;

        for (TabEntry value : tabEntries.values()) {
            clearFakeTeams(value);

            if(value.initialized) {
                asNMS(value.player).connection.send(new ClientboundSetObjectivePacket(
                        objective,
                        ClientboundSetObjectivePacket.METHOD_REMOVE
                ));
            }
        }
        tabEntries.clear();
        task.cancel();
        task = null;
    }

    private void clearFakeTeams(TabEntry entry) {
        if(entry.fakeTeams.isEmpty()) return;

        PacketBundleBuilder builder = new PacketBundleBuilder();
        for (PlayerTeam oldTeam : entry.fakeTeams) {
            builder.add(ClientboundSetPlayerTeamPacket.createRemovePacket(oldTeam));
        }
        entry.fakeTeams.clear();

        asNMS(entry.player).connection.connection.send(builder.build());
    }

    private void updateAsync() {
        try {
            update();
        }
        catch (Exception e) {
            LOGGER.warning(LoggerUtil.exceptionToString(e));
        }
    }

    private void update() {
        List<TabEntry> list = new ArrayList<>(tabEntries.values());
        list.sort(TabEntry::compareTo);

        int listSize = list.size();

        String objectiveFormat = configuration.objective;

        // At first: update tab name for each player
        for (int i = 0; i < listSize; i++) {
            TabEntry entry = list.get(i);
            Player player = entry.player;

            ServerPlayer serverPlayer = asNMS(player);
            serverPlayer.listName = PaperAdventure.asVanilla(Text.create(Placeholders.resolve(
                    configuration.playerName,
                    Placeholder.create("player_name", player::getName),
                    Placeholder.createFinal("sort_order", String.valueOf(i + 1))
            )));

            if(objectiveFormat.isEmpty()) {
                entry.objectiveValue = null;
                continue;
            }

            Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
            assert objective != null;
            if(!entry.initialized) {
                serverPlayer.connection.send(new ClientboundSetObjectivePacket(
                        objective,
                        ClientboundSetObjectivePacket.METHOD_ADD
                ));
                serverPlayer.connection.send(new ClientboundSetDisplayObjectivePacket(
                        DisplaySlot.LIST,
                        objective
                ));
                entry.initialized = true;
            }

            entry.objectiveValue = new FixedFormat(PaperAdventure.asVanilla(Text.create(Placeholders.resolveDynamic(
                    objectiveFormat,
                    player,
                    OBJECTIVE_PLACEHOLDERS
            ))));
        }

        int maxIndexLength = String.valueOf(listSize).length();

        for (TabEntry entry : list) {
            // Clear old team data
            clearFakeTeams(entry);

            PacketBundleBuilder builder = new PacketBundleBuilder();

            // iterate through every entry for every player
            for (int i = 0; i < listSize; i++) {
                TabEntry other = list.get(i);
                Player player = other.player;
                ServerPlayer serverPlayer = asNMS(player);
                if(entry != other) {
                    Pair<Boolean, Boolean> listed = other.isListed(entry);
                    if(listed.first())
                        builder.add(ClientboundPlayerInfoUpdatePacket.updateListed(player.getUniqueId(), listed.second()));

                    if(!listed.second()) continue;
                }

                String playerName = player.getName();
                String teamName = String.format("%0" + maxIndexLength + "d", i);

                PlayerTeam team = new PlayerTeam(scoreboard, teamName);
                team.setNameTagVisibility(Team.Visibility.NEVER);
                team.getPlayers().add(playerName);
                entry.fakeTeams.add(team);

                builder.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
                builder.add(new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        serverPlayer
                ));

                if(entry.initialized) {
                    builder.add(new ClientboundSetScorePacket(
                            playerName,
                            OBJECTIVE_NAME,
                            1,
                            Optional.empty(),
                            Optional.ofNullable(other.objectiveValue)
                    ));
                }
            }

            ServerPlayer viewer = asNMS(entry.player);
            viewer.connection.send(builder.build());

            // update header and footer
            viewer.connection.send(new ClientboundTabListPacket(
                    buildHeaderOrFooter(configuration.header, entry.player, HEADER_FOOTER_PLACEHOLDERS),
                    buildHeaderOrFooter(configuration.footer, entry.player, HEADER_FOOTER_PLACEHOLDERS)
            ));
        }
    }

    private Component buildHeaderOrFooter(List<String> lines, Player player, DynamicPlaceholder<Player>[] placeholders) {
        return PaperAdventure.asVanilla(Text.create(Placeholders.resolveDynamic(
                String.join("\n", lines),
                player,
                placeholders
        )));
    }

    private void addPlayer(Player player) {
        if(tabEntries.containsKey(player)) return;
        uxModule.storage().getUserDataContainer(player)
                .thenAccept(container -> addPlayer(player, container));
    }

    public void addPlayer(Player player, DataContainer container) {
        if(tabEntries.containsKey(player)) return;
        tabEntries.put(player, new TabEntry(player, container, sorters, player::canSee));
    }

    public void removePlayer(Player player) {
        TabEntry entry = tabEntries.remove(player);
        if(entry == null) return;

        for (TabEntry value : tabEntries.values()) {
            value.unlisted.remove(entry);
        }
    }

    private void sendPacket(Packet<?> packet) {
        for (Player player : tabEntries.keySet()) {
            asNMS(player).connection.send(packet);
        }
    }

}
