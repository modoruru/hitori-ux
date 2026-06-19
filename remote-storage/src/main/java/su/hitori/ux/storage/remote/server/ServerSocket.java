package su.hitori.ux.storage.remote.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public final class ServerSocket extends WebSocketServer {

    private boolean locked;

    public ServerSocket(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket client, ClientHandshake handshake) {

    }

    @Override
    public void onClose(WebSocket client, int code, String reason, boolean remote) {

    }

    @Override
    public void onMessage(WebSocket client, String message) {

    }

    @Override
    public void onError(WebSocket client, Exception ex) {

    }

    @Override
    public void onStart() {

    }

}
