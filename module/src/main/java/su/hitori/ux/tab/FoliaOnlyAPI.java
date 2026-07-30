package su.hitori.ux.tab;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;

final class FoliaOnlyAPI {

    FoliaOnlyAPI() {
    }

    public double[] getRegionTPS(Location location) {
        return Bukkit.getServer().getRegionTPS(location);
    }

    public double getRegionMSPT(Location location) {
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();
        return world.regioniser.getRegionAtSynchronised(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                .getData()
                .getRegionSchedulingHandle()
                .getTickReport5s(System.nanoTime())
                .timePerTickData()
                .segmentAll()
                .average() / 1.0E6;
    }

}
