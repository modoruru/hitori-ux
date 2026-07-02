package su.hitori.ux.remotestorage.test;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;

import java.net.URI;

public final class TestClient extends WebSocketClient {

    private final String expectedResponse;

    public TestClient(URI serverUri, String expectedResponse) {
        super(serverUri);
        this.expectedResponse = expectedResponse;
    }

    public boolean active() {
        return isOpen();
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        send(
                new JSONObject()
                        .put("type", "storage_connect")
                        .put("user", "survival_server")
                        .put("password", "veryCoolPassword123")
                        .toString()
        );
    }

    @Override
    public void onMessage(String message) {
        Assertions.assertEquals(expectedResponse, message);

        try {
            closeBlocking();
        }
        catch (InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception ex) {

    }

}
