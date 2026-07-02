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

public class ServerSocket extends WebSocketServer {

    protected final ServerConfiguration serverConfiguration;
    protected final ExecutorService executorService;
    protected final DatabaseHandle databaseHandle;

    protected final Map<UUID, Client> clients;
    protected final Map<String, Client> indexByUser;

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

        if(serverConfiguration.verboseLoggingChannel != null)
            serverConfiguration.verboseLoggingChannel.accept(String.format(
                    "Created connection with %s and assigned %s uuid.",
                    client.getRemoteSocketAddress().getAddress().getHostAddress(),
                    wrapper.uuid
            ));
    }

    @Override
    public void onClose(WebSocket client, int code, String reason, boolean remote) {
        UUID uuid = client.getAttachment();
        if(uuid == null) return; // tf?

        Client wrapper = clients.remove(uuid);
        assert wrapper != null;

        if(serverConfiguration.verboseLoggingChannel != null)
            serverConfiguration.verboseLoggingChannel.accept(String.format(
                    "Closed connection with %s.",
                    wrapper.authorized ? wrapper.user : wrapper.uuid.toString()
            ));

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
        assert wrapper != null;

        if(serverConfiguration.verboseLoggingChannel != null)
            serverConfiguration.verboseLoggingChannel.accept(String.format(
                    "Received \"%s\" message from %s.",
                    type,
                    wrapper.authorized ? wrapper.user : wrapper.uuid.toString()
            ));

        handleCompleteMessage(wrapper, type, messageBody);
    }

    protected void handleCompleteMessage(Client wrapper, String type, JSONObject messageBody) {
        if(!wrapper.authorized) {
            if(!type.equalsIgnoreCase("storage_connect")) {
                wrapper.socket.closeConnection(CloseFrame.REFUSE, "not authorized");
                return;
            }

            String user = messageBody.optString("user");
            String password = messageBody.optString("password");
            if(user.isEmpty() || password.isEmpty() || indexByUser.get(user) != null) {
                wrapper.socket.closeConnection(CloseFrame.POLICY_VALIDATION, "wrong credentials");
                return;
            }

            String realPassword = serverConfiguration.users.get(user);
            if(realPassword == null || !realPassword.equals(password)) {
                wrapper.socket.closeConnection(CloseFrame.POLICY_VALIDATION, "wrong credentials");
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

            if(serverConfiguration.verboseLoggingChannel != null) {
                serverConfiguration.verboseLoggingChannel.accept(String.format(
                        "Client %s is now authorized under %s username.",
                        wrapper.uuid.toString(),
                        wrapper.user
                ));
            }

            return;
        }

        switch (type.toLowerCase()) {
            case "storage_data_push" -> {
                String rawUuid = messageBody.optString("uuid", null);
                if(rawUuid == null || rawUuid.isEmpty()) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "missing \"uuid\" field.");
                    return;
                }

                UUID containerUuid = parseUuid(rawUuid);
                if(containerUuid == null) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "unable to decode uuid.");
                    return;
                }

                String field = messageBody.optString("field", null);
                if(field == null || field.isEmpty()) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "missing \"field\" field.");
                    return;
                }

                Identifier identifier = databaseHandle.completeIdentifier(containerUuid, null, null);
                if(identifier == null) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "requested value push to an unknown container.");
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
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "missing \"uuid\" field.");
                    return;
                }

                UUID containerUuid = parseUuid(rawUuid);
                if(containerUuid == null) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "unable to decode uuid.");
                    return;
                }

                if(!databaseHandle.exists(containerUuid)) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "requested tracking on unknown container.");
                    return;
                }

                boolean trackingStatus = messageBody.optBoolean("tracking_status");

                if(trackingStatus) wrapper.tracking.add(containerUuid);
                else wrapper.tracking.remove(containerUuid);
            }
            case "view_container" -> {
                JSONObject identifierBody = messageBody.optJSONObject("identifier");
                if(identifierBody == null) {
                    wrapper.socket.closeConnection(CloseFrame.REFUSE, "missing \"identifier\" field.");
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

    public static UUID parseUuid(String string) {
        if(string == null || string.isEmpty()) return null;

        try {
            return UUID.fromString(string);
        }
        catch (IllegalArgumentException _) {
            return null;
        }
    }

    protected void sendAsync(Client wrapper, String message) {
        if(serverConfiguration.verboseLoggingChannel != null)
            serverConfiguration.verboseLoggingChannel.accept(String.format(
                    "Sent message to %s.",
                    wrapper.authorized ? wrapper.user : wrapper.uuid.toString()
            ));

        executorService.execute(() -> wrapper.socket.send(message));
    }

    @Override
    public void onError(WebSocket client, Exception ex) {

    }

    @Override
    public void onStart() {
        if(serverConfiguration.verboseLoggingChannel != null)
            serverConfiguration.verboseLoggingChannel.accept("Socket successfully started and ready to accept connections.");
    }

    protected static final class Client {

        protected final WebSocket socket;
        protected final UUID uuid;

        protected final Set<UUID> tracking;

        protected String user;
        protected boolean authorized;

        Client(WebSocket socket) {
            this.socket = socket;
            this.uuid = UUID.randomUUID();

            this.tracking = new HashSet<>();
        }

    }

}
