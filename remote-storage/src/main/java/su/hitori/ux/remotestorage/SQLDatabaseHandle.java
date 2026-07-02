package su.hitori.ux.remotestorage;

import org.json.JSONObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SQLDatabaseHandle implements DatabaseHandle {

    private final String connectString;
    private final ScheduledExecutorService executorService;
    private final long cacheRetainTime;

    private final Map<UUID, ContainerWrapper> cache;

    private Connection connection;

    /**
     * @param cacheRetainTime set to 0 to disable caching.
     */
    public SQLDatabaseHandle(String connectString, ScheduledExecutorService executorService, long cacheRetainTime) {
        this.connectString = connectString;
        this.executorService = executorService;
        this.cacheRetainTime = cacheRetainTime;

        this.cache = new HashMap<>();
    }

    public void connect() {
        if(connection != null) return;
        try {
            connection = DriverManager.getConnection(connectString);

            try (Statement index = createStatement(); Statement users = createStatement()) {
                index.execute("CREATE TABLE IF NOT EXISTS `index` (uuid TEXT PRIMARY KEY, game_uuid TEXT, game_name TEXT)");
                users.execute("CREATE TABLE IF NOT EXISTS users (uuid TEXT PRIMARY KEY, body TEXT)");
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected Statement createStatement() throws SQLException {
        return connection.createStatement();
    }

    protected PreparedStatement prepareStatement(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

    @Override
    public boolean exists(UUID container) {
        ContainerWrapper wrapper;
        if(cacheRetainTime > 0 && (wrapper = cache.get(container)) != null) {
            renewRemovalTask(wrapper);
            return true;
        }

        try (PreparedStatement statement = prepareStatement("SELECT game_uuid FROM `index` WHERE uuid = ?")) {
            statement.setString(1, container.toString());
            return statement.executeQuery().next();
        }
        catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void set(UUID container, String field, Object value) {
        JSONObject json = null;
        if(cacheRetainTime > 0) {
            ContainerWrapper wrapper = cache.get(container);
            if(wrapper != null) {
                renewRemovalTask(wrapper);
                json = wrapper.container;
            }
        }

        if(json == null) json = viewContainer(container);

        json.put(field, value);

        saveContainer(container, json);
    }

    private void saveContainer(UUID container, JSONObject body) {
        try (PreparedStatement statement = prepareStatement(
                "UPDATE users SET body = ? WHERE uuid = ?"
        )) {
            statement.setString(1, body.toString());
            statement.setString(2, container.toString());
            statement.execute();
        }
        catch (SQLException e) {
            throw new RuntimeException();
        }
    }

    @Override
    public Identifier completeIdentifier(UUID uuid, UUID gameUuid, String gameName) {
        boolean uuidPresent = uuid != null;

        if(uuidPresent && cacheRetainTime > 0) {
            ContainerWrapper wrapper = cache.get(uuid);
            if(wrapper != null) {
                renewRemovalTask(wrapper);
                return wrapper.identifier;
            }
        }

        try (PreparedStatement statement = prepareStatement(String.format(
                "SELECT %s FROM `index` WHERE %s = %s",
                uuidPresent ? "game_uuid, game_name" : "uuid, game_uuid",
                uuidPresent ? "uuid" : "LOWER(game_name)",
                gameName == null ? "?" : "LOWER(?)"
        ))) {
            statement.setString(1, uuidPresent ? uuid.toString() : gameName);
            ResultSet set = statement.executeQuery();
            if(!set.next()) {
                if(gameUuid == null || gameName == null) return null;

                Identifier identifier = new Identifier(
                        uuid == null
                                ? (gameName.isEmpty()
                                   ? new UUID(0, 0)
                                   : UUID.randomUUID())
                                : uuid,
                        gameUuid,
                        gameName
                );
                executorService.execute(() -> {
                    try (PreparedStatement insertIdentifier = prepareStatement("INSERT INTO `index` (uuid, game_uuid, game_name) VALUES (?, ?, ?)")) {
                        insertIdentifier.setString(1, identifier.uuid().toString());
                        insertIdentifier.setString(2, identifier.gameUuid().toString());
                        insertIdentifier.setString(3, identifier.gameName());
                        insertIdentifier.execute();
                    }
                    catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });

                // we should wait for this to finish so viewContainer request will not fail
                try (PreparedStatement insertContainer = prepareStatement("INSERT INTO users (uuid, body) VALUES (?, ?)")) {
                    insertContainer.setString(1, identifier.uuid().toString());
                    insertContainer.setString(2, "{}");
                    insertContainer.execute();
                }
                catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                return identifier;
            }

            return new Identifier(
                    uuid == null ? UUID.fromString(set.getString("uuid")) : uuid,
                    UUID.fromString(set.getString("game_uuid")),
                    gameName == null ? set.getString("game_name") : gameName
            );
        }
        catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void updateIdentifier(UUID uuid, UUID newGameUuid, String newGameName) {
        try (PreparedStatement statement = prepareStatement("UPDATE `index` SET game_uuid = ?, game_name = ? WHERE uuid = ?")) {
            statement.setString(1, newGameUuid.toString());
            statement.setString(2, newGameName);
            statement.setString(3, uuid.toString());
            statement.execute();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JSONObject viewContainer(UUID container) {
        if(cacheRetainTime > 0) {
            ContainerWrapper wrapper = cache.get(container);
            if(wrapper != null) {
                renewRemovalTask(wrapper);
                return new JSONObject(wrapper.container.toMap());
            }
        }

        try (PreparedStatement statement = prepareStatement("SELECT body FROM users WHERE uuid = ?")) {
            statement.setString(1, container.toString());
            ResultSet result = statement.executeQuery();

            if(!result.next()) return null;

            String rawBody = result.getString("body");

            JSONObject json;
            if(rawBody == null || rawBody.isEmpty()) json = new JSONObject();
            else json = new JSONObject(rawBody);

            if(cacheRetainTime > 0) {
                Identifier identifier = completeIdentifier(container, null, null);
                ContainerWrapper wrapper = new ContainerWrapper(identifier, new JSONObject(json.toMap()));
                renewRemovalTask(wrapper);
                cache.put(container, wrapper);
            }

            return json;
        }
        catch (Throwable exception) {
            throw new RuntimeException(exception);
        }
    }

    private void renewRemovalTask(ContainerWrapper wrapper) {
        if(wrapper.removalTask != null)
            wrapper.removalTask.cancel(true);

        wrapper.removalTask = executorService.schedule(
                () -> {
                    cache.remove(wrapper.identifier.uuid());
                    return null;
                },
                cacheRetainTime,
                TimeUnit.MILLISECONDS
        );
    }

    private static class ContainerWrapper {

        final JSONObject container;
        final Identifier identifier;
        ScheduledFuture<Void> removalTask;

        ContainerWrapper(Identifier identifier, JSONObject container) {
            this.identifier = identifier;
            this.container = container;
        }

    }

}
