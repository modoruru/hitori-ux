package su.hitori.ux.remotestorage;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;

public final class ServerSocket extends WebSocketServer {

    private final ServerConfiguration serverConfiguration;
    private final ExecutorService executorService;
    private final DatabaseHandle databaseHandle;

    private final Map<UUID, Client> clients;
    private final Map<String, Client> indexByUser;

    public ServerSocket(ServerConfiguration serverConfiguration, ExecutorService executorService, DatabaseHandle databaseHandle) {
        super(new InetSocketAddress(serverConfiguration.port));

        this.serverConfiguration = serverConfiguration;
        this.executorService = executorService;
        this.databaseHandle = databaseHandle;

        this.clients = new HashMap<>();
        this.indexByUser = new HashMap<>();
    }

    @Override
    public void onOpen(WebSocket client, ClientHandshake handshake) {
        Client wrapper = new Client(client);
        clients.put(wrapper.uuid, wrapper);

        client.setAttachment(wrapper.uuid);
    }

    @Override
    public void onClose(WebSocket client, int code, String reason, boolean remote) {
        UUID uuid = client.getAttachment();
        if(uuid == null) return; // tf?
        Client wrapper = clients.remove(uuid);
        assert wrapper != null;

        if(wrapper.user != null)
            indexByUser.remove(wrapper.user);
    }

    @Override
    public void onMessage(WebSocket client, String message) {
        UUID uuid = client.getAttachment();
        if(uuid == null) return; // tf?

        JSONObject messageBody;
        try {
             messageBody = new JSONObject(message);
        }
        catch (JSONException _) {
            client.closeConnection(CloseFrame.REFUSE, "not a json");
            return;
        }

        String type = messageBody.optString("type", null);
        if(type == null) {
            client.closeConnection(CloseFrame.REFUSE, "missing \"type\" field.");
            return;
        }

        Client wrapper = clients.get(uuid);
        if(!wrapper.authorized) {
            if(!type.equalsIgnoreCase("storage_connect")) {
                client.closeConnection(CloseFrame.REFUSE, "not authorized");
                return;
            }

            String user = messageBody.optString("user");
            String password = messageBody.optString("password");
            if(user.isEmpty() || password.isEmpty() || indexByUser.get(user) != null) {
                client.closeConnection(CloseFrame.POLICY_VALIDATION, "wrong credentials");
                return;
            }

            String realPassword = serverConfiguration.users.get(user);
            if(realPassword == null || !realPassword.equals(password)) {
                client.closeConnection(CloseFrame.POLICY_VALIDATION, "wrong credentials");
                return;
            }

            wrapper.user = user;
            wrapper.authorized = true;
            indexByUser.put(user, wrapper);
            sendAsync(
                    wrapper,
                    new JSONObject()
                            .put("type", "storage_connect")
                            .put("success", true)
                            .toString()
            );
            return;
        }

        switch (type.toLowerCase()) {
            case "storage_data_push" -> {
                String rawUuid = messageBody.optString("uuid", null);
                if(rawUuid == null || rawUuid.isEmpty()) {
                    client.closeConnection(CloseFrame.REFUSE, "missing \"uuid\" field.");
                    return;
                }

                UUID containerUuid = parseUuid(rawUuid);
                if(containerUuid == null) {
                    client.closeConnection(CloseFrame.REFUSE, "unable to decode uuid.");
                    return;
                }

                String field = messageBody.optString("field", null);
                if(field == null || field.isEmpty()) {
                    client.closeConnection(CloseFrame.REFUSE, "missing \"field\" field.");
                    return;
                }

                Identifier identifier = databaseHandle.completeIdentifier(uuid, null, null);
                if(identifier == null) {
                    client.closeConnection(CloseFrame.REFUSE, "requested value push to an unknown container.");
                    return;
                }

                Object value = messageBody.opt("value");
                databaseHandle.set(containerUuid, field, value);

                String trackingMessage = null;
                for (Client wrapper0 : clients.values()) {
                    if(wrapper0 == wrapper) continue;

                    if(!wrapper0.tracking.contains(containerUuid)) continue;

                    if(trackingMessage == null) {
                        trackingMessage = new JSONObject()
                                .put("type", "tracking")
                                .put(
                                        "identifier",
                                        new JSONObject()
                                                .put("uuid", containerUuid.toString())
                                                .put("game_uuid", identifier.gameUuid().toString())
                                                .put("game_name", identifier.gameName())
                                )
                                .put("field", field)
                                .put("value", value)
                                .toString();
                    }

                    sendAsync(wrapper0, trackingMessage);
                }
            }
            case "complete_identifier" -> {
                Identifier identifier = databaseHandle.completeIdentifier(
                        parseUuid(messageBody.optString("uuid")),
                        parseUuid(messageBody.optString("game_uuid")),
                        messageBody.optString("game_name", null)
                );
                if(identifier == null) {
                    sendAsync(
                            wrapper,
                            new JSONObject()
                                    .put("type", "complete_identifier")
                                    .put("request_uuid", messageBody.optString("request_uuid"))
                                    .put("success", false)
                                    .toString()
                    );
                    return;
                }

                sendAsync(
                        wrapper,
                        new JSONObject()
                                .put("type", "complete_identifier")
                                .put("request_uuid", messageBody.optString("request_uuid"))
                                .put("uuid", identifier.uuid().toString())
                                .put("game_uuid", identifier.gameUuid().toString())
                                .put("game_name", identifier.gameName())
                                .put("success", true)
                                .toString()
                );
            }
            case "tracking" -> {
                String rawUuid = messageBody.optString("uuid", null);
                if(rawUuid == null || rawUuid.isEmpty()) {
                    client.closeConnection(CloseFrame.REFUSE, "missing \"uuid\" field.");
                    return;
                }

                UUID containerUuid = parseUuid(rawUuid);
                if(containerUuid == null) {
                    client.closeConnection(CloseFrame.REFUSE, "unable to decode uuid.");
                    return;
                }

                if(!databaseHandle.exists(containerUuid)) {
                    client.closeConnection(CloseFrame.REFUSE, "requested tracking on unknown container.");
                    return;
                }

                boolean trackingStatus = messageBody.optBoolean("tracking_status");

                if(trackingStatus) wrapper.tracking.add(containerUuid);
                else wrapper.tracking.remove(containerUuid);
            }
            case "view_container" -> {
                JSONObject identifierBody = messageBody.optJSONObject("identifier");
                if(identifierBody == null) {
                    client.closeConnection(CloseFrame.REFUSE, "missing \"identifier\" field.");
                    return;
                }

                Identifier identifier = databaseHandle.completeIdentifier(
                        parseUuid(identifierBody.optString("uuid")),
                        parseUuid(identifierBody.optString("game_uuid")),
                        identifierBody.optString("game_name", null)
                );
                if(identifier == null) {
                    sendAsync(
                            wrapper,
                            new JSONObject()
                                    .put("type", "view_container")
                                    .put("request_uuid", messageBody.optString("request_uuid"))
                                    .put("success", false)
                                    .toString()
                    );
                    return;
                }

                sendAsync(
                        wrapper,
                        new JSONObject()
                                .put("type", "view_container")
                                .put(
                                        "identifier",
                                        new JSONObject()
                                                .put("uuid", identifier.uuid().toString())
                                                .put("game_uuid", identifier.gameUuid().toString())
                                                .put("game_name", identifier.gameName())
                                )
                                .put("container", new JSONObject(databaseHandle.viewContainer(identifier.uuid()).toMap()))
                                .put("request_uuid", messageBody.optString("request_uuid"))
                                .put("success", true)
                                .toString()
                );
            }
        }
    }

    private static UUID parseUuid(String string) {
        if(string == null || string.isEmpty()) return null;

        try {
            return UUID.fromString(string);
        }
        catch (IllegalArgumentException _) {
            return null;
        }
    }

    private void sendAsync(Client wrapper, String message) {
        executorService.execute(() -> wrapper.socket.send(message));
    }

    @Override
    public void onError(WebSocket client, Exception ex) {}

    @Override
    public void onStart() {}

    private static final class Client {

        final WebSocket socket;
        final UUID uuid;

        final Set<UUID> tracking;

        String user;
        boolean authorized;

        Client(WebSocket socket) {
            this.socket = socket;
            this.uuid = UUID.randomUUID();

            this.tracking = new HashSet<>();
        }

    }

}
