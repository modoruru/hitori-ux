package su.hitori.ux.storage.remote;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.*;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;
import su.hitori.ux.storage.def.AsyncPlayerSynchronizationEvent;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class RemoteStorage implements Storage<RemoteDataContainer> {

    protected static final int REQUEST_TIMEOUT_SECONDS = 30;

    protected static final Identifier SERVER_DATA_IDENTIFIER = new Identifier(
            new UUID(0, 0),
            new UUID(0, 0),
            ""
    );

    protected static final Logger LOGGER = LoggerFactory.instance().create(RemoteStorage.class);

    protected final ScheduledExecutorService executorService;
    protected final ClientSocket clientSocket;
    protected final String user;
    protected final String password;

    private final Map<UUID, CachedRequest> requestCache;
    private final Map<UUID, UUID> requestedUuidToRequestUuidCache, requestedGameUuidToRequestUuidCache;
    private final Map<String, UUID> requestedGameNameToRequestUuidCache;
    private final Map<UUID, CompletableFuture<@Nullable Identifier>> identifierRequests;

    private final Map<Identifier, RemoteDataContainer> dataCache;
    private final Map<UUID, Identifier> identifierCacheByUuid, identifierCacheByGameUuid;
    private final Map<String, Identifier> identifierCacheByGameName;

    private CompletableFuture<Void> openFuture;

    final Map<String, DataField<?>> userDataScheme;
    final Map<String, DataField<?>> serverDataScheme;

    private boolean syncAllPlayers;
    private ConnectionState state = ConnectionState.NEVER_OPENED;
    private int connectionAttempts;
    private ScheduledFuture<Boolean> delayedConnectionAttempt;
    private Thread delayedConnectionThread;

    private Task removeTemporaryTask;

    public RemoteStorage(ScheduledExecutorService executorService, URI uri, String user, String password) {
        this.executorService = executorService;
        this.clientSocket = new ClientSocket(uri, this, this::handleMessage);
        this.user = user;
        this.password = password;

        this.requestCache = new ConcurrentHashMap<>();
        this.requestedUuidToRequestUuidCache = new ConcurrentHashMap<>();
        this.requestedGameUuidToRequestUuidCache = new ConcurrentHashMap<>();
        this.requestedGameNameToRequestUuidCache = new ConcurrentHashMap<>();
        this.identifierRequests = new ConcurrentHashMap<>();

        this.identifierCacheByUuid = new ConcurrentHashMap<>();
        this.identifierCacheByGameUuid = new ConcurrentHashMap<>();
        this.identifierCacheByGameName = new ConcurrentHashMap<>();

        this.dataCache = new ConcurrentHashMap<>();
        this.openFuture = new CompletableFuture<>();

        this.userDataScheme = new HashMap<>();
        this.serverDataScheme = new HashMap<>();
    }

    // メソッドをオーバーライドする可能性を残しておく
    protected void handleMessage(JSONObject messageBody) {
        switch (messageBody.optString("type", "").toLowerCase()) {
            case "storage_connect" -> {
                if(state != ConnectionState.OPENED) break;

                boolean success = messageBody.optBoolean("success", false);
                if(success) {
                    LOGGER.info("RemoteStorage initialized!");

                    if(removeTemporaryTask == null) {
                        removeTemporaryTask = Task.runTaskTimerGlobally(() -> executorService.execute(() -> {
                            if(state != ConnectionState.AUTHORIZED) return;

                            Set<Identifier> toClose = new HashSet<>();
                            for (RemoteDataContainer container : dataCache.values()) {
                                if(container.temporary && System.currentTimeMillis() > (container.lastAccess + RemoteDataContainer.RETAINING_TIME_SECONDS * 1000L)) {
                                    toClose.add(container.identifier());
                                }
                            }
                            toClose.forEach(this::quit);

                        }), 1L, 20L);
                    }

                    openFuture.complete(null);

                    state = ConnectionState.AUTHORIZED;

                    if(syncAllPlayers) {
                        Bukkit.getOnlinePlayers().forEach(this::syncPlayer);
                        syncAllPlayers = false;
                    }
                }
                else {
                    printLockMessage(-1, String.format(
                            "Unable to authenticate to RemoteStorage server: %s",
                            messageBody.optString("error", "no error present")
                    ), connectionAttempts + 1);

                    openFuture.completeExceptionally(new IllegalStateException("Unable to authenticate to RemoteStorage server."));
                    state = ConnectionState.CLOSED;
                    internalClose(true);
                }
            }
            case "tracking" -> {
                if(state != ConnectionState.AUTHORIZED) break;

                JSONObject identifierBody = messageBody.optJSONObject("identifier");
                if(identifierBody == null) {
                    LOGGER.warning("Unable to decode \"tracking\" message: " + messageBody);
                    return;
                }

                Identifier identifier = RemoteStorageUtil.decodeIdentifier(identifierBody);
                String field = messageBody.optString("field");
                if(field.isEmpty()) {
                    LOGGER.warning("Unable to decode \"tracking\" message, \"field\" field is empty.");
                    return;
                }

                DataField<?> dataField = (SERVER_DATA_IDENTIFIER.equals(identifier) ? serverDataScheme : userDataScheme).get(field);
                if(dataField == null) {
                    if(UXConfiguration.I.storage.remoteImplementation.nonExistingFieldUpdateWarning)
                        LOGGER.warning("Unable to accept \"tracking\" message, DataField \"" + field + "\" doesn't exists on our end.");
                    return;
                }

                RemoteDataContainer remoteDataContainer = dataCache.get(identifier);
                if(remoteDataContainer == null || remoteDataContainer.isClosed()) return;

                if(UXConfiguration.I.storage.remoteImplementation.verboseLogging)
                    LOGGER.info("Received tracking for " + remoteDataContainer.identifier().toString() + " for field: " + field);

                remoteDataContainer.setDirect(UnsafeUtil.cast(dataField), dataField.codec().decode(messageBody.opt("value")));
            }
            case "view_container" -> {
                if(state != ConnectionState.AUTHORIZED) break;

                String rawRequestUuid = messageBody.optString("request_uuid");
                if(rawRequestUuid == null || rawRequestUuid.isEmpty())
                    return; // some shit is happening there

                UUID requestUuid;
                try {
                    requestUuid = UUID.fromString(rawRequestUuid);
                }
                catch (IllegalArgumentException _) {
                    // some shit is happening there again
                    return;
                }

                CachedRequest request = requestCache.remove(requestUuid);
                if(request == null) return;

                boolean success = messageBody.optBoolean("success");
                if(!success) {
                    request.request().complete(null);
                    return;
                }

                JSONObject identifierBody = messageBody.optJSONObject("identifier");
                if(identifierBody == null) {
                    LOGGER.warning("Unable to decode \"view_container\" message: " + messageBody);
                    return;
                }

                request.request().complete(createAndInitializeContainer(
                        RemoteStorageUtil.decodeIdentifier(identifierBody),
                        request.temporary(),
                        messageBody.optJSONObject("container")
                ));
            }
            case "complete_identifier" -> {
                UUID uuid = UUID.fromString(messageBody.optString("request_uuid"));

                var request = identifierRequests.remove(uuid);
                if(request == null) return;

                request.complete(RemoteStorageUtil.decodeIdentifier(messageBody));
            }
            default -> {}
        }
    }

    protected RemoteDataContainer createAndInitializeContainer(Identifier identifier, boolean temporary, JSONObject containerBody) {
        if(dataCache.get(identifier) != null) return dataCache.get(identifier);

        requestedUuidToRequestUuidCache.remove(identifier.uuid());
        requestedGameUuidToRequestUuidCache.remove(identifier.gameUuid());
        requestedGameNameToRequestUuidCache.remove(identifier.gameName());

        RemoteDataContainer container = new RemoteDataContainer(
                this,
                identifier,
                (SERVER_DATA_IDENTIFIER.equals(identifier) ? serverDataScheme : userDataScheme).values(),
                temporary
        );

        if(containerBody != null) container.initialize(containerBody);

        if(UXConfiguration.I.storage.remoteImplementation.verboseLogging)
            LOGGER.info(identifier.gameName() + " container is now cached and tracked");

        dataCache.put(identifier, container);
        identifierCacheByUuid.put(identifier.uuid(), identifier);
        identifierCacheByGameUuid.put(identifier.gameUuid(), identifier);
        identifierCacheByGameName.put(identifier.gameName().toLowerCase(), identifier);

        trackingStatus(identifier.uuid(), true);

        Player player = getPlayerByIdentifier(identifier);
        if(player != null)
            Task.async(() -> Bukkit.getPluginManager().callEvent(new AsyncPlayerSynchronizationEvent(player, container)), 1L);

        return container;
    }

    @Override
    public void addFieldsToUserScheme(DataField<?>... fields) {
        addFields(true, fields);
    }

    @Override
    public void addFieldsToServerScheme(DataField<?>... fields) {
        addFields(false, fields);
    }

    protected void addFields(boolean userScheme, DataField<?>... fields) {
        if(isInitialized())
            throw new IllegalStateException("RemoteStorage doesn't allows changing scheme after initiation");

        Map<String, DataField<?>> scheme = (userScheme ? userDataScheme : serverDataScheme);
        for (DataField<?> field : fields) {
            scheme.put(field.name(), field);
        }
    }

    @Override
    public boolean isInitialized() {
        return state == ConnectionState.AUTHORIZED;
    }

    @Override
    public void open(boolean syncAllPlayers) {
        if(state != ConnectionState.NEVER_OPENED) return;

        this.syncAllPlayers = syncAllPlayers;

        executorService.execute(() -> connect(false));
    }

    boolean connect(boolean reconnection) {
        try {
            if((reconnection ? !clientSocket.reconnectBlocking() : !clientSocket.connectBlocking())) {
                connectionClosed(-1, "Unable to connect", false);
                return false;
            }
            LOGGER.info("Connected to the endpoint");

            state = ConnectionState.OPENED;

            clientSocket.send(
                    new JSONObject()
                            .put("type", "storage_connect")
                            .put("user", user)
                            .put("password", password)
                            .toString()
            );
            LOGGER.info("Sent connection packet");

            connectionAttempts = 0;

            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    void connectionClosed(int code, String reason, boolean immediate) {
        var config = UXConfiguration.I.storage.remoteImplementation;
        int allowedAttempts = config.reconnectAttempts;
        if(allowedAttempts <= 0 || connectionAttempts >= allowedAttempts) {
            state = ConnectionState.CLOSED;
            printLockMessage(code, reason, connectionAttempts + 1);
            internalClose(true);
            return;
        }

        state = ConnectionState.RECONNECTING;

        if (!openFuture.isDone())
            openFuture.completeExceptionally(new TimeoutException());

        openFuture = new CompletableFuture<>();

        for (RemoteDataContainer container : dataCache.values()) {
            container.close(false);
        }
        dataCache.clear();

        syncAllPlayers = true;
        delayedConnectionThread = null;

        if(!immediate && ++connectionAttempts > 1) {
            delayedConnectionAttempt = executorService.schedule(() -> {
                delayedConnectionThread = Thread.currentThread();
                return connect(true);
            }, config.reconnectAttemptDelay, TimeUnit.SECONDS);
            return;
        }

        executorService.execute(() -> connect(true));
    }

    protected static void printLockMessage(int code, String reason, int attempts) {
        final String separator = "========================================";
        LOGGER.severe(separator);
        LOGGER.severe("The connection to the storage could not be established. Server is now locked - non of the players can join.");
        LOGGER.severe("Restart the module or, what better, restart the server.");
        LOGGER.severe("Below is the most detailed information the module can provide about why the connection was lost.");
        LOGGER.severe(String.format("WebSocket close code - %s, reason - \"%s\", connection attempts: %s", code, reason, attempts));
        LOGGER.severe(separator);
    }

    protected final void internalClose(boolean kickPlayers) {
        try {
            if(delayedConnectionAttempt != null && delayedConnectionThread != Thread.currentThread())
                delayedConnectionAttempt.cancel(true);

            for (CompletableFuture<@Nullable Identifier> value : identifierRequests.values()) {
                value.cancel(true);
            }

            for (CachedRequest value : requestCache.values()) {
                value.request().cancel(true);
            }

            for (RemoteDataContainer value : dataCache.values()) {
                value.close(false);

                if(kickPlayers) {
                    Player player = getPlayerByIdentifier(value.identifier());
                    if(player != null) Task.ensureSync(() -> player.kick(Component.text("Internal error")));
                }
            }

            if(!clientSocket.isClosed() && !clientSocket.isClosing())
                clientSocket.closeBlocking();
        }
        catch (Throwable exception) {
            LOGGER.severe(LoggerUtil.exceptionToString(exception));
        }
        finally {
            removeTemporaryTask.cancel();
            removeTemporaryTask = null;

            requestCache.clear();
            requestedUuidToRequestUuidCache.clear();
            requestedGameUuidToRequestUuidCache.clear();
            requestedGameNameToRequestUuidCache.clear();
            identifierRequests.clear();

            dataCache.clear();
            identifierCacheByUuid.clear();
            identifierCacheByGameUuid.clear();
            identifierCacheByGameName.clear();

            delayedConnectionAttempt = null;
        }
    }

    @Override
    public void close() {
        if(state == ConnectionState.NEVER_OPENED || state == ConnectionState.CLOSED) return;
        state = ConnectionState.CLOSED;
        internalClose(false);
    }

    protected void syncPlayer(Player player) {
        long start = System.currentTimeMillis();
        getUserDataContainer(
                null,
                player.getUniqueId(),
                player.getName(),
                true,
                true
        ).thenAccept(_ -> {
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            player.sendActionBar(Text.create(String.format(
                    "Synchronized in %sms <green>✔</green>",
                    System.currentTimeMillis() - start
            )));
        });
    }

    protected void quit(Player player) {
        Identifier identifier = identifierCacheByGameName.remove(player.getName().toLowerCase());
        if(identifier == null) return;

        identifierCacheByUuid.remove(identifier.uuid());
        identifierCacheByGameUuid.remove(identifier.gameUuid());

        RemoteDataContainer container = dataCache.get(identifier);
        if(container == null) return;
        container.temporary = true;
        container.lastAccess = System.currentTimeMillis();

        if(UXConfiguration.I.storage.remoteImplementation.verboseLogging)
            LOGGER.info(identifier.gameName() + " container is now marked as temporary.");
    }

    protected void quit(Identifier identifier) {
        RemoteDataContainer container = dataCache.remove(identifier);

        identifierCacheByUuid.remove(identifier.uuid());
        identifierCacheByGameUuid.remove(identifier.gameUuid());
        identifierCacheByGameName.remove(identifier.gameName().toLowerCase());

        if(container == null) return;
        container.close(true);

        if(UXConfiguration.I.storage.remoteImplementation.verboseLogging)
            LOGGER.info(identifier.gameName() + " container was closed.");
    }

    protected void trackingStatus(UUID uuid, boolean status) {
        if(state != ConnectionState.AUTHORIZED) return;

        executorService.execute(() -> clientSocket.send(
                new JSONObject()
                        .put("type", "tracking")
                        .put("uuid", uuid.toString())
                        .put("tracking_status", status)
                        .toString()
        ));
    }

    @Override
    public CompletableFuture<Set<Identifier>> getAllIdentifiers() {
        throw new UnsupportedOperationException("not implemented currently");
    }

    @Override
    public Player getPlayerByIdentifier(Identifier identifier) {
        return Bukkit.getPlayer(identifier.gameName());
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getServerDataContainer() {
        return getUserDataContainer(SERVER_DATA_IDENTIFIER, true, true);
    }

    protected final CachedRequest findCachedRequest(UUID uuid, UUID gameUuid, String gameName) {
        UUID requestUuid = null;

        if(uuid != null) requestUuid =  requestedUuidToRequestUuidCache.get(uuid);
        if(requestUuid == null && gameUuid != null) requestUuid = requestedGameUuidToRequestUuidCache.get(gameUuid);
        if(requestUuid == null && gameName != null) requestUuid = requestedGameNameToRequestUuidCache.get(gameName.toLowerCase());

        if(requestUuid == null) return null;

        return requestCache.get(requestUuid);
    }

    protected final RemoteDataContainer findCachedData(UUID uuid, UUID gameUuid, String gameName) {
        Identifier identifier = null;

        if(uuid != null) identifier = identifierCacheByUuid.get(uuid);
        if(identifier == null && gameUuid != null) identifier = identifierCacheByGameUuid.get(gameUuid);
        if(identifier == null && gameName != null) identifier = identifierCacheByGameName.get(gameName.toLowerCase());

        if(identifier == null) return null;

        return dataCache.get(identifier);
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getUserDataContainer(Identifier identifier, boolean requestIfNotCached, boolean cache) {
        return getUserDataContainer(identifier.uuid(), identifier.gameUuid(), identifier.gameName(), requestIfNotCached, cache);
    }

    public CompletableFuture<RemoteDataContainer> getUserDataContainer(UUID uuid, UUID gameUuid, String gameName, boolean requestIfNotCached, boolean cache) {
        if(state == ConnectionState.CLOSED || (uuid == null && gameUuid == null && gameName == null))
            return CompletableFuture.completedFuture(null);

        RemoteDataContainer cachedData = findCachedData(uuid, gameUuid, gameName);
        if(cachedData != null) {
            if(cachedData.temporary && cache) {
                cachedData.temporary = false;
                if(UXConfiguration.I.storage.remoteImplementation.verboseLogging)
                    LOGGER.info(cachedData.identifier().toString() + " became temporary.");
            }
            return CompletableFuture.completedFuture(cachedData);
        }

        CachedRequest cachedRequest = findCachedRequest(uuid, gameUuid, gameName);
        if(cachedRequest != null && !cachedRequest.request().isDone()) return cachedRequest.request();

        if(!requestIfNotCached) return CompletableFuture.completedFuture(null);

        UUID requestUuid = UUID.randomUUID();
        openFuture.thenRun(() -> createViewRequest(uuid, gameUuid, gameName, requestUuid));
        CompletableFuture<RemoteDataContainer> future = new CompletableFuture<>();

        requestCache.put(requestUuid, new CachedRequest(future, !cache));

        if(!Bukkit.isStopping()) {
            Task.runGlobally(() -> {
                if (!future.isDone()) {
                    future.completeExceptionally(new TimeoutException("View container request timed out"));
                    cleanupRequest(requestUuid, uuid, gameUuid, gameName);
                }
            }, 20L * REQUEST_TIMEOUT_SECONDS);
        }

        if(uuid != null) requestedUuidToRequestUuidCache.put(uuid, requestUuid);
        if(gameUuid != null) requestedGameUuidToRequestUuidCache.put(gameUuid, requestUuid);
        if(gameName != null) requestedGameNameToRequestUuidCache.put(gameName.toLowerCase(), requestUuid);

        return future;
    }

    protected void cleanupRequest(UUID requestUuid, UUID uuid, UUID gameUuid, String gameName) {
        requestCache.remove(requestUuid);
        if (uuid != null) requestedUuidToRequestUuidCache.remove(uuid);
        if (gameUuid != null) requestedGameUuidToRequestUuidCache.remove(gameUuid);
        if (gameName != null) requestedGameNameToRequestUuidCache.remove(gameName.toLowerCase());
    }

    protected void pushValueAsync(UUID uuid, String field, Object value) {
        executorService.execute(() -> {
            JSONObject messageBody = new JSONObject()
                    .put("type", "storage_data_push")
                    .put("uuid", uuid.toString())
                    .put("field", field);
            if(value != null) messageBody.put("value", value);

            clientSocket.send(messageBody.toString());
        });
    }

    protected void createViewRequest(UUID uuid, UUID gameUuid, String gameName, UUID requestUuid) {
        JSONObject requestBody = new JSONObject()
                .put("type", "view_container")
                .put("request_uuid", requestUuid.toString());

        JSONObject identifierBody = new JSONObject();
        if(uuid != null) identifierBody.put("uuid", uuid.toString());
        if(gameUuid != null) identifierBody.put("game_uuid", gameUuid.toString());
        if(gameName != null) identifierBody.put("game_name", gameName);

        requestBody.put("identifier", identifierBody);

        clientSocket.send(requestBody.toString());
    }

    protected void createCompleteRequest(UUID uuid, String gameName, UUID requestUuid) {
        JSONObject requestBody = new JSONObject()
                .put("type", "complete_identifier")
                .put("request_uuid", requestUuid.toString());

        if(uuid != null) requestBody.put("uuid", uuid.toString());
        if(gameName != null) requestBody.put("game_name", gameName);

        clientSocket.send(requestBody.toString());
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getUserDataContainer(Player player) {
        return getUserDataContainer(
                null,
                player.getUniqueId(),
                player.getName(),
                true,
                false
        );
    }

    @Override
    public CompletableFuture<@Nullable Identifier> getIdentifier(Either<UUID, String> uuidOrGameName) {
        if(uuidOrGameName.firstPresent()) {
            Identifier identifier = identifierCacheByUuid.get(uuidOrGameName.first());
            if(identifier != null) return CompletableFuture.completedFuture(identifier);
        }

        if(uuidOrGameName.secondPresent()) {
            Identifier identifier = identifierCacheByGameName.get(uuidOrGameName.second().toLowerCase());
            if(identifier != null) return CompletableFuture.completedFuture(identifier);
        }

        UUID requestUuid = UUID.randomUUID();
        executorService.execute(() -> createCompleteRequest(
                uuidOrGameName.firstOptional().orElse(null),
                uuidOrGameName.secondOptional().orElse(null),
                requestUuid
        ));

        CompletableFuture<@Nullable Identifier> future = new CompletableFuture<>();
        identifierRequests.put(requestUuid, future);

        if(!Bukkit.isStopping()) {
            Task.runGlobally(() -> {
                if (!future.isDone()) {
                    future.completeExceptionally(new TimeoutException("Identifier request timed out"));
                    identifierRequests.remove(requestUuid);
                }
            }, 20L * REQUEST_TIMEOUT_SECONDS);
        }

        return future;
    }

}
