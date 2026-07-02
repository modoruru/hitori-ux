package su.hitori.ux.remotestorage;

import org.json.JSONObject;

import java.util.UUID;

public interface DatabaseHandle {

    boolean exists(UUID container);

    void set(UUID container, String field, Object value);

    Identifier completeIdentifier(UUID uuid, UUID gameUuid, String gameName);

    void updateIdentifier(UUID uuid, UUID newGameUuid, String newGameName);

    JSONObject viewContainer(UUID container);

}
