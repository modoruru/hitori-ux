package su.hitori.ux.storage;

import org.jetbrains.annotations.NotNull;

import java.sql.*;

final class SQLHandle {

    private Connection connection;

    SQLHandle() {
    }

    private static void initH2Driver() {
        try {
            Class.forName("org.h2.Driver").getConstructor().newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void open(@NotNull String url, @NotNull String user, @NotNull String password, String initialQuery) throws SQLException {
        if(url.startsWith("jdbc:h2")) initH2Driver();

        connection = DriverManager.getConnection(url + ";DB_CLOSE_ON_EXIT=FALSE", user, password);

        if(initialQuery == null) return;

        try (Statement statement = createStatement()) {
            statement.execute(initialQuery);
        }
    }

    void close() throws SQLException {
        connection.close();
    }

    Statement createStatement() throws SQLException {
        return connection.createStatement();
    }

    PreparedStatement prepareStatement(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

}
