package su.hitori.ux.notification;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.hitori.api.util.Text;
import su.hitori.ux.Sound;

public final class Notifications {

    public void sendNotification(@NotNull Player player, @NotNull Notification notification) {
        sendNotification(player, notification.type(), notification.text(), notification.sound());
    }

    public void sendNotification(@NotNull Player player, @NotNull NotificationType type, @NotNull String miniMessageText, @Nullable Sound sound) {
        AsyncNotificationEvent event = new AsyncNotificationEvent(
                player,
                type,
                miniMessageText,
                sound
        );
        if(!event.callEvent()) return;

        player.sendActionBar(Text.create(event.text()));

        Sound sound1 = event.sound();
        if(sound1 != null) sound1.playFor(player);
    }

}
