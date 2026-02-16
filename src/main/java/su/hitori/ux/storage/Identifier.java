package su.hitori.ux.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class Identifier {

    private final UUID uuid, gameUuid;
    private final String gameName;

    private final int hash;

    /**
     * @param uuid system uuid
     * @param gameUuid minecraft uuid
     * @param gameName minecraft username
     */
    public Identifier(@NotNull UUID uuid, @NotNull UUID gameUuid, @NotNull String gameName) {
        this.uuid = uuid;
        this.gameUuid = gameUuid;
        this.gameName = gameName;
        this.hash = Objects.hash(uuid, gameUuid, gameName.toLowerCase());
    }

    public UUID uuid() {
        return uuid;
    }

    public UUID gameUuid() {
        return gameUuid;
    }

    public String gameName() {
        return gameName;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Identifier that)) return false;
        return
                Objects.equals(uuid, that.uuid)
                && Objects.equals(gameUuid, that.gameUuid)
                && Objects.equals(gameName.toLowerCase(), that.gameName.toLowerCase());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return String.format(
                "Identifier(id: %s, game_id: %s, game_name: \"%s\")",
                uuid.toString(),
                gameUuid.toString(),
                gameName
        );
    }

}
