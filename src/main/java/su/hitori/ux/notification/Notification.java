package su.hitori.ux.notification;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.hitori.api.Hitori;
import su.hitori.api.module.ModuleDescriptor;
import su.hitori.ux.Sound;
import su.hitori.ux.UXModule;

public record Notification(@NotNull NotificationType type, @NotNull String text, @Nullable Sound sound) {

    public void send(@NotNull Player player) {
        Hitori.instance().moduleRepository().getModule(Key.key("hitori", "ux"))
                .map(ModuleDescriptor::getInstance)
                .map(module -> (UXModule) module)
                .map(UXModule::notifications)
                .ifPresent(notifications -> notifications.sendNotification(player, this));
    }

}
