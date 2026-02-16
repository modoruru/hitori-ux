package su.hitori.ux.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import su.hitori.api.container.Container;
import su.hitori.api.util.Text;

import java.util.UUID;

public final class SharedInventoryContainer implements Container {

    private final UUID uuid;
    private final Inventory inventory;

    SharedInventoryContainer(Player player) {
        uuid = UUID.randomUUID();
        inventory = Bukkit.createInventory(this, 36, Text.create(player.getName() + " inventory"));

        PlayerInventory playerInventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            int index;
            if(i < 9) index = 27 + i;
            else index = i - 9;
            inventory.setItem(index, playerInventory.getItem(i));
        }
    }

    public UUID uuid() {
        return uuid;
    }

    @Override
    public void click(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void drag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void close(InventoryCloseEvent event) {

    }

    void close() {
        for (HumanEntity viewer : inventory.getViewers()) {
            viewer.closeInventory();
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

}
