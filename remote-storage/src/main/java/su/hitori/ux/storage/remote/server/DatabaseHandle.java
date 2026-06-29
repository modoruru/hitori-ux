package su.hitori.ux.storage.remote.server;


import java.util.Map;
import java.util.UUID;

public interface DatabaseHandle {

    boolean exists(UUID container);

    void set(UUID container, String field, Object value);

    Object get(UUID container, String field);

    Identifier completeIdentifier(UUID uuid, UUID gameUuid, String gameName);

    Map<String, Object> viewContainer(UUID container);

}
