package su.hitori.ux.storage.remote.server;

import java.util.HashMap;
import java.util.Map;

public final class ServerConfiguration {

    final int port;
    final Map<String, String> users;

    private ServerConfiguration(int port, Map<String, String> users) {
        this.port = port;
        this.users = users;
    }

    public static Builder serverConfiguration(int port) {
        return new Builder(port);
    }

    public static final class Builder {

        private final int port;
        private final Map<String, String> users;

        private Builder(int port) {
            this.port = port;
            this.users = new HashMap<>();
        }

        public Builder addUser(String user, String password) {
            users.put(user, password);
            return this;
        }

        public Builder addUsers(Map<String, String> users) {
            this.users.putAll(users);
            return this;
        }

        public ServerConfiguration build() {
            return new ServerConfiguration(port, Map.copyOf(users));
        }

    }

}
