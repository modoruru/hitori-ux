package su.hitori.ux.remotestorage.test;

import su.hitori.ux.remotestorage.SQLDatabaseHandle;
import su.hitori.ux.remotestorage.ServerConfiguration;
import su.hitori.ux.remotestorage.ServerSocket;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ExampleServer {

    public static final Map<String, String> users = Map.of(
            "survival_server", "veryCoolPassword123"
    );
    public static final String databaseFile = "test.db";
    public static final int databaseCacheRetainSeconds = 30;



    private final ServerSocket serverSocket;
    private final SQLDatabaseHandle sqlDatabaseHandle;
    private volatile boolean running;

    public ExampleServer(int port) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        this.serverSocket = new ServerSocket(
                ServerConfiguration.serverConfiguration(port)
                        .verboseLoggingChannel(message -> System.out.printf("[ServerSocket] %s\n", message))
                        .addUsers(users)
                        .build(),
                executorService,
                sqlDatabaseHandle = new SQLDatabaseHandle(
                        "jdbc:sqlite:" + databaseFile,
                        executorService,
                        databaseCacheRetainSeconds * 1000L
                )
        );
    }

    public void start() {
        if(running) return;

        sqlDatabaseHandle.connect();
        serverSocket.start();
        running = true;
    }

    public void stop() {
        if(!running) return;
        running = false;

        try {
            serverSocket.stop();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
