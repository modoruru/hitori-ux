package su.hitori.ux.remotestorage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ServerConfiguration {

    final int port;
    final Consumer<String> verboseLoggingChannel;
    final Map<String, String> users;

    private ServerConfiguration(int port, Consumer<String> verboseLoggingChannel, Map<String, String> users) {
        this.port = port;
        this.verboseLoggingChannel = verboseLoggingChannel;
        this.users = users;
    }

    public static Builder serverConfiguration(int port) {
        return new Builder(port);
    }

    public static final class Builder {

        private final int port;
        private Consumer<String> verboseLoggingChannel;
        private final Map<String, String> users;

        private Builder(int port) {
            this.port = port;
            this.users = new HashMap<>();
        }

        public Builder verboseLoggingChannel(Consumer<String> verboseLoggingChannel) {
            this.verboseLoggingChannel = verboseLoggingChannel;
            return this;
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
            return new ServerConfiguration(port, verboseLoggingChannel, Map.copyOf(users));
        }

    }

}
