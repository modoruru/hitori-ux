package su.hitori.ux.storage.remote;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

public final class ClientSocket extends WebSocketClient {

    private final RemoteStorage remoteStorage;
    private final MessageHandler messageHandler;

    public ClientSocket(URI serverUri, RemoteStorage remoteStorage, MessageHandler messageHandler) {
        super(serverUri);
        this.remoteStorage = remoteStorage;
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
        if(remote)
            remoteStorage.connectionClosed(code, reason, true);
    }

    @Override
    public void onError(Exception ex) {
    }

}
