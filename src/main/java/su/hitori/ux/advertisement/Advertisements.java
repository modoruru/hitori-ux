package su.hitori.ux.advertisement;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.hitori.api.util.Task;
import su.hitori.api.util.Text;
import su.hitori.ux.config.UXConfiguration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class Advertisements {

    private Task sendTask;
    private Task stayTask;

    public Advertisements() {
    }

    public void start() {
        var config = UXConfiguration.I.advertisements;

        if(sendTask != null || !UXConfiguration.I.advertisements.enabled) return;

        sendTask = Task.runTaskTimerAsync(this::send, 0L, config.period * 20L);
    }

    private void send() {
        var config = UXConfiguration.I.advertisements;
        List<String> list = config.list;
        if(list.isEmpty()) return;

        int index = (int) (Math.random() * (list.size() - 1));
        Component advertisement = Text.create(list.get(index));

        Set<Player> toReceive = Bukkit.getOnlinePlayers().parallelStream()
                .filter(player -> new AsyncAdvertisementEvent(player, advertisement).callEvent())
                .collect(Collectors.toSet());

        if(toReceive.isEmpty()) return;

        long stayLength = config.stay * 20L;
        stayTask = Task.runTaskTimerAsync(() ->
                toReceive.forEach(player -> player.sendActionBar(advertisement)),
                0L, 1L
        );

        Task.async(() -> {
            if(stayTask != null) stayTask.cancel();
        }, stayLength);
    }

    public void stop() {
        if(sendTask == null) return;

        sendTask.cancel();
        if(stayTask != null)
            stayTask.cancel();

        sendTask = null;
        stayTask = null;
    }

}
