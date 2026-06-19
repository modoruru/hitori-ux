package su.hitori.ux.storage.remote.client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

public final class ClientSocket extends WebSocketClient {

    private final MessageHandler messageHandler;

    public ClientSocket(URI serverUri, MessageHandler messageHandler) {
        super(serverUri);
        this.messageHandler = messageHandler;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {

    }

    @Override
    public void onMessage(String message) {
        JSONObject messageBody;

        try {
            messageBody = new JSONObject(message);

            messageHandler.message(messageBody);
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.printf("Connection closed. code: %s, reason: \"%s\"\n", code, reason);
    }

    @Override
    public void onError(Exception ex) {
    }

}
