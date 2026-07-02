package su.hitori.ux.remotestorage.test;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

public final class ServerTest {

    @Test
    public void testAuthentication() throws InterruptedException {
        int port = getAvailablePort();

        ExampleServer server = new ExampleServer(port);
        server.start();

        TestClient client = new TestClient(
                URI.create("ws://localhost:" + port),
                new JSONObject()
                        .put("type", "storage_connect")
                        .put("success", "true")
                        .toString()
        );

        client.connectBlocking();

        while (client.active()) {
            Thread.onSpinWait();
        }

        server.stop();
    }

    public static int getAvailablePort() throws InterruptedException {
        while (true) {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                serverSocket.setReuseAddress(true);
                return serverSocket.getLocalPort();
            }
            catch (IOException _) {
            }
            Thread.sleep(10);
        }
    }

}
