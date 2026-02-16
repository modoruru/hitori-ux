package su.hitori.ux.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgumentLike;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import su.hitori.api.util.Task;
import su.hitori.api.util.Text;
import su.hitori.ux.Sound;
import su.hitori.ux.UXModule;
import su.hitori.ux.chat.event.AsyncFirstVisitMessageEvent;
import su.hitori.ux.chat.event.AsyncPlayerOnlineMessageEvent;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.AsyncPlayerSynchronizationEvent;
import su.hitori.ux.storage.DataContainer;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("resource")
public final class ChatListener implements Listener {

    private final UXModule uxModule;
    private final Map<Player, Task> showFirstVisitTasks;

    public ChatListener(UXModule uxModule) {
        this.uxModule = uxModule;
        this.showFirstVisitTasks = new HashMap<>();
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        callOnlineMessageEvent(player, true);

        UUID uuid = player.getUniqueId();
        Bukkit.getOnlinePlayers().parallelStream()
                .filter(player1 -> player1 != player)
                .forEach(alreadyOnline ->
                        uxModule.chat().seenJoinOf.computeIfAbsent(alreadyOnline, _ -> new HashSet<>()).add(uuid)
                );
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        callOnlineMessageEvent(player, false);

        Task task = showFirstVisitTasks.remove(player);
        if(task != null) task.cancel();

        Chat chat = uxModule.chat();

        chat.lastDM.remove(player.getName().toLowerCase());
        chat.seenJoinOf.remove(player);
        chat.deleteSharedInventory(player);

        UUID uuid = player.getUniqueId();
        for (Set<UUID> set : chat.seenJoinOf.values()) {
            set.remove(uuid);
        }

        chat.seenJoinOf.values().removeIf(Set::isEmpty);
    }

    private void callOnlineMessageEvent(Player player, boolean nowOnline) {
        uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
            uxModule.executorService().execute(() -> {
                var config = UXConfiguration.I.chat.joinQuit;
                AsyncPlayerOnlineMessageEvent event = new AsyncPlayerOnlineMessageEvent(player, container, Text.create(Placeholders.resolveDynamic(
                        (nowOnline ? config.join : config.quit).convert().determine(container),
                        player,
                        Chat.PLAYER_NAME_PLACEHOLDER
                )), nowOnline);
                if(!event.callEvent()) return;

                Bukkit.broadcast(event.message());
            });
        });
    }

    @EventHandler
    private void onAsyncPlayerSynchronization(AsyncPlayerSynchronizationEvent event) {
        Player player = event.player();

        var firstVisitConfig = UXConfiguration.I.chat.firstVisit;
        if(!firstVisitConfig.enabled) return;

        uxModule.storage().getUserDataContainer(event.identifier(), true, false).thenAccept(container -> {
            if(container == null) return;

            if(container.getOrDefault(Chat.SEEN_FIRST_VISIT_MESSAGE_FIELD, false)) return;

            int playedSeconds = (int) (player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20d);
            if(playedSeconds > firstVisitConfig.secondsToSpend) {
                showFirstVisit(player, container);
                return;
            }

            showFirstVisitTasks.put(
                    player,
                    Task.runGlobally(
                            () -> showFirstVisit(player, container),
                            (firstVisitConfig.secondsToSpend - playedSeconds) * 20L
                    )
            );
        });
    }

    private void showFirstVisit(Player player, DataContainer container) {
        uxModule.executorService().execute(() -> {
            var config = UXConfiguration.I.chat.firstVisit;

            AsyncFirstVisitMessageEvent event = new AsyncFirstVisitMessageEvent(player, Text.create(Placeholders.resolveDynamic(
                    config.message.convert().determine(container),
                    player,
                    Chat.PLAYER_NAME_PLACEHOLDER
            )), config.sound.convert());
            event.callEvent();

            container.set(Chat.SEEN_FIRST_VISIT_MESSAGE_FIELD, true);
            player.sendMessage(event.message());

            Sound sound = event.sound();
            if(sound != null) sound.playFor(player);
        });
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent event) {
        event.deathMessage(replaceVanillaUsername(event.deathMessage(), event.getPlayer()));
    }

    private static Component replaceVanillaUsername(Component original, Player player) {
        if(!(original instanceof TranslatableComponent translatable)) return original;

        List<Component> arguments = translatable.arguments()
                .parallelStream()
                .map(TranslationArgumentLike::asComponent)
                .collect(Collectors.toList());
        arguments.set(0, Text.create(Placeholders.resolveDynamic(
                UXConfiguration.I.chat.replaceVanillaUsername.usernameFormat,
                player,
                Chat.PLAYER_NAME_PLACEHOLDER
        )));

        return Component.translatable(translatable.key(), arguments.toArray(new Component[0]));
    }

    @EventHandler
    private void onAsyncChat(AsyncChatEvent event) {
        event.setCancelled(true);
        uxModule.chat().chatMessage(event.getPlayer(), event.message());
    }

}
