package su.hitori.ux.remotestorage;

import org.json.JSONObject;

import java.util.UUID;

public interface DatabaseHandle {

    boolean exists(UUID container);

    void set(UUID container, String field, Object value);

    Object get(UUID container, String field);

    Identifier completeIdentifier(UUID uuid, UUID gameUuid, String gameName);

    JSONObject viewContainer(UUID container);

}
