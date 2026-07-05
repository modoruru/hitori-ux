package su.hitori.ux.storage.remote;

import org.json.JSONObject;
import su.hitori.ux.storage.Identifier;

import java.util.UUID;

public final class RemoteStorageUtil {

    private RemoteStorageUtil() {}

    public static Identifier decodeIdentifier(JSONObject json) {
        return new Identifier(
                UUID.fromString(json.optString("uuid")),
                UUID.fromString(json.optString("game_uuid")),
                json.optString("game_name")
        );
    }

}
