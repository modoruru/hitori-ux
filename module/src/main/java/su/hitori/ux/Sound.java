package su.hitori.ux;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import su.hitori.ux.storage.serialize.JSONCodec;

public record Sound(String name, float volume, float pitch) {

    public static final JSONCodec<Sound> CODEC = new JSONCodec<>(
            sound -> String.format("%s:%s:%s", sound.name, sound.volume, sound.pitch),
            obj -> {
                String[] split = ((String) obj).split(":", 3);
                return new Sound(split[0], Float.parseFloat(split[1]), Float.parseFloat(split[2]));
            }
    );

    public void playFor(Player player) {
        playFor(player, SoundCategory.MASTER);
    }

    public void playFor(Player player, SoundCategory category) {
        player.playSound(player, name, category, volume, pitch);
    }

    public void playFrom(Entity entity) {
        playFrom(entity, SoundCategory.MASTER);
    }

    public void playFrom(Entity entity, SoundCategory category) {
        entity.getWorld().playSound(entity, name, category, volume, pitch);
    }

}
