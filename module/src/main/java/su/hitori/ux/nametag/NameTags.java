package su.hitori.ux.nametag;

import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import su.hitori.api.module.ModuleDescriptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class NameTags {

    static final NamespacedKey FOR_REMOVAL = new NamespacedKey("hitori", "for_removal");

    private final AtomicReference<ModuleDescriptor> resourcePackModuleReference;
    final PlayerTeam playerTeam;
    final Map<UUID, NameTagEntity> tags;

    private SkinsRestorerHook skinsRestorerHook;
    long lastTeamUpdate;

    private boolean started;

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
        if(started) return;

        started = true;

        for (Player player : Bukkit.getOnlinePlayers()) {
            track(player);
        }

        if(Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) return;
        skinsRestorerHook = new SkinsRestorerHook();
        skinsRestorerHook.register(this);
    }

    public void stop() {
        if(!started) return;

        started = false;

        boolean serverStopping = Bukkit.isStopping();

        for (NameTagEntity nameTagEntity : tags.values()) {
            if(serverStopping) nameTagEntity.textDisplay.getPersistentDataContainer().set(FOR_REMOVAL, PersistentDataType.BOOLEAN, true);
            nameTagEntity.remove(!serverStopping);
        }
        tags.clear();

        if(skinsRestorerHook != null) skinsRestorerHook.unregister();
    }

    void track(Player player) {
        if(tags.containsKey(player.getUniqueId())) return;
        tags.put(player.getUniqueId(), NameTagEntity.create(this, player));
        lastTeamUpdate = System.currentTimeMillis();
    }

    void untrack(Player player) {
        NameTagEntity nameTagEntity = tags.remove(player.getUniqueId());
        if(nameTagEntity == null) return;
        nameTagEntity.remove(true);
    }

    void forceUpdate(Player player) {
        NameTagEntity nameTagEntity = tags.get(player.getUniqueId());
        if(nameTagEntity != null) nameTagEntity.update(playerTeam, lastTeamUpdate > nameTagEntity.lastTeamUpdate);
    }

    void forceResendPassengers(Player player) {
        NameTagEntity nameTagEntity = tags.get(player.getUniqueId());
        if(nameTagEntity != null) nameTagEntity.resendPassengers();
    }

}
