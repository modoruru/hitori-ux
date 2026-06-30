package su.hitori.ux.remotestorage;

import org.json.JSONObject;

import java.sql.*;
import java.util.UUID;

public class SQLDatabaseHandle implements DatabaseHandle {

    private final String connectString;

    private Connection connection;

    public SQLDatabaseHandle(String connectString) {
        this.connectString = connectString;
    }

    public void connect() {
        if(connection != null) return;
        try {
            connection = DriverManager.getConnection(connectString + ";DB_CLOSE_ON_EXIT=FALSE");

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
        return false;
    }

    @Override
    public void set(UUID container, String field, Object value) {

    }

    @Override
    public Object get(UUID container, String field) {
        return null;
    }

    @Override
    public Identifier completeIdentifier(UUID uuid, UUID gameUuid, String gameName) {
        boolean uuidPresent = uuid != null;

        try (PreparedStatement statement = prepareStatement(String.format(
                "SELECT %s FROM `index` WHERE %s = %s",
                uuidPresent ? "game_uuid, game_name" : "uuid, game_uuid",
                uuidPresent ? "uuid" : "LOWER(game_name)",
                gameName == null ? "?" : "LOWER(?)"
        ))) {
            statement.setString(1, uuidPresent ? uuid.toString() : gameName);
            ResultSet set = statement.executeQuery();
            if(!set.next()) return null;

            return new Identifier(
                    uuid == null ? UUID.fromString(set.getString("uuid")) : uuid,
                    UUID.fromString(set.getString("game_uuid")),
                    gameName == null ? set.getString("game_name") : gameName
            );
        }
        catch (Throwable ex) {
            throw  new RuntimeException(ex);
        }
    }

    @Override
    public JSONObject viewContainer(UUID container) {
        return null;
    }

}
