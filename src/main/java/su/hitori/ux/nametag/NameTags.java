package su.hitori.ux.nametag;

import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.hitori.api.module.ModuleDescriptor;
import su.hitori.api.util.Task;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class NameTags {

    private final AtomicReference<ModuleDescriptor> resourcePackModuleReference;
    private final PlayerTeam playerTeam;
    private final Map<Player, NameTagEntity> tags;

    private Task task;
    private boolean teamDirty;

    public NameTags(AtomicReference<ModuleDescriptor> resourcePackModuleReference) {
        this.resourcePackModuleReference = resourcePackModuleReference;
        this.playerTeam = new PlayerTeam(new Scoreboard(), "nametags");
        this.tags = new HashMap<>();

        playerTeam.setNameTagVisibility(Team.Visibility.NEVER);
    }

    boolean isLying(Player player) {
        ModuleDescriptor descriptor = resourcePackModuleReference.get();
        if(descriptor == null) return false;
        ClassLoader classLoader = descriptor.classLoader();

        try {
            Class<?> packModule_class = classLoader.loadClass("su.hitori.pack.PackModule");
            Method packModule_poseService_method = packModule_class.getMethod("poseService");

            Object poseService = packModule_poseService_method.invoke(descriptor.getInstance());
            Method poseService_getLyingPoseByRider_method = poseService.getClass().getMethod("getLyingPoseByRider", Player.class);

            Object lyingPose = poseService_getLyingPoseByRider_method.invoke(poseService, player);
            return lyingPose != null;
        }
        catch (Exception _) {
            return false;
        }
    }

    public void start() {
        if(task != null) return;
        task = Task.runTaskTimerGlobally(this::update, 0L, 20L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            track(player);
        }
    }

    public void stop() {
        if(task == null) return;
        task.cancel();
        task = null;

        for (NameTagEntity nameTagEntity : tags.values()) {
            nameTagEntity.remove();
        }
        tags.clear();
    }
    
    void track(Player player) {
        if(tags.containsKey(player)) return;
        tags.put(player, NameTagEntity.create(this, player));
        teamDirty = true;
    }
    
    void untrack(Player player) {
        NameTagEntity nameTagEntity = tags.remove(player);
        if(nameTagEntity == null) return;
        nameTagEntity.remove();
    }

    void forceUpdate(Player player) {
        NameTagEntity nameTagEntity = tags.get(player);
        if(nameTagEntity != null) nameTagEntity.update(playerTeam, teamDirty);
    }

    void forceResendPassengers(Player player) {
        NameTagEntity nameTagEntity = tags.get(player);
        if(nameTagEntity != null) nameTagEntity.resendPassengers();
    }

    private void update() {
        for (NameTagEntity nameTagEntity : tags.values()) {
            nameTagEntity.update(playerTeam, teamDirty);
        }
        if(teamDirty) teamDirty = false;
    }

}
