package su.hitori.ux.storage.remote;

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

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class RemoteStorage implements Storage<RemoteDataContainer> {

    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    private static final Identifier SERVER_DATA_IDENTIFIER = new Identifier(
            new UUID(0, 0),
            new UUID(0, 0),
            ""
    );

    private static final Logger LOGGER = LoggerFactory.instance().create(RemoteStorage.class);

    protected final ExecutorService executorService;
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

    private final CompletableFuture<Void> openFuture;

    final Map<String, DataField<?>> userDataScheme;
    final Map<String, DataField<?>> serverDataScheme;

    private boolean syncAllPlayers;
    private boolean initialized;
    private boolean closed;
    private Task removeTemporaryTask;

    public RemoteStorage(ExecutorService executorService, URI uri, String user, String password) {
        this.executorService = executorService;
        this.clientSocket = new ClientSocket(uri, this::handleMessage);
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
                if(initialized || closed) break;

                boolean success = messageBody.optBoolean("success", false);
                if(success) {
                    initialized = true;
                    LOGGER.info("RemoteStorage initialized!");

                    removeTemporaryTask = Task.runTaskTimerGlobally(() -> executorService.execute(() -> {
                        if(!initialized || closed) return;

                        Set<Identifier> toClose = new HashSet<>();
                        for (RemoteDataContainer container : dataCache.values()) {
                            if(container.temporary && System.currentTimeMillis() > (container.lastAccess + RemoteDataContainer.RETAINING_TIME_SECONDS * 1000L)) {
                                toClose.add(container.identifier());
                            }
                        }
                        toClose.forEach(this::quit);

                    }), 0L, 20L);

                    openFuture.complete(null);

                    if(syncAllPlayers)
                        Bukkit.getOnlinePlayers().forEach(this::syncPlayer);
                }
                else {
                    LOGGER.warning(String.format(
                            "Unable to authenticate RemoteStorage server: %s",
                            messageBody.optString("error", "no error present")
                    ));
                    openFuture.completeExceptionally(new IllegalStateException("Unable to authenticate to RemoteStorage server."));
                    closed = true;
                }
            }
            case "tracking" -> {
                if(!initialized || closed) break;

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
                if(remoteDataContainer == null) return;

                remoteDataContainer.set(UnsafeUtil.cast(dataField), dataField.codec().decode(messageBody.opt("value")));
            }
            case "view_container" -> {
                if(!initialized || closed) break;

                JSONObject identifierBody = messageBody.optJSONObject("identifier");
                if(identifierBody == null) {
                    LOGGER.warning("Unable to decode \"tracking\" message: " + messageBody);
                    return;
                }

                Identifier identifier = RemoteStorageUtil.decodeIdentifier(identifierBody);
                var request = findCachedRequest(identifier.uuid(), identifier.gameUuid(), identifier.gameName(), true);
                if(request == null) {
                    LOGGER.warning(String.format(
                            "Received view_container message for [uuid: %s, game_uuid: %s, game_name: %s] without requesting it :)",
                            identifier.uuid(),
                            identifier.gameUuid(),
                            identifier.gameName()
                    ));
                    return;
                }

                RemoteDataContainer container = new RemoteDataContainer(
                        this,
                        identifier,
                        (SERVER_DATA_IDENTIFIER.equals(identifier) ? serverDataScheme : userDataScheme).values(),
                        request.cache()
                );

                JSONObject containerBody = messageBody.optJSONObject("container");
                if(containerBody != null) container.initialize(containerBody);

                dataCache.put(identifier, container);
                identifierCacheByUuid.put(identifier.uuid(), identifier);
                identifierCacheByGameUuid.put(identifier.gameUuid(), identifier);
                identifierCacheByGameName.put(identifier.gameName().toLowerCase(), identifier);

                trackingStatus(identifier.uuid(), true);

                request.request().complete(container);
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

    @Override
    public void addFieldsToUserScheme(DataField<?>... fields) {
        addFields(true, fields);
    }

    @Override
    public void addFieldsToServerScheme(DataField<?>... fields) {
        addFields(false, fields);
    }

    private void addFields(boolean userScheme, DataField<?>... fields) {
        if(isInitialized())
            throw new IllegalStateException("RemoteStorage doesn't allows changing scheme after initiation");

        Map<String, DataField<?>> scheme = (userScheme ? userDataScheme : serverDataScheme);
        for (DataField<?> field : fields) {
            scheme.put(field.name(), field);
        }
    }

    @Override
    public boolean isInitialized() {
        return openFuture.isDone();
    }

    @Override
    public void open(boolean syncAllPlayers) {
        if(isInitialized()) return;

        this.syncAllPlayers = syncAllPlayers;

        CompletableFuture.supplyAsync(() -> {
            try {
                if(!clientSocket.connectBlocking()) {
                    LOGGER.warning("Unable to connect to RemoteStorage.");
                    throw new IllegalStateException();
                }
                LOGGER.info("Connected to endpoint");

                clientSocket.send(
                        new JSONObject()
                                .put("type", "storage_connect")
                                .put("user", user)
                                .put("password", password)
                                .toString()
                );
                LOGGER.info("Sent connection packet");
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }

            return null;
        }, executorService).whenComplete((_, error) -> {
            if(error != null) {
                closed = true;
                openFuture.complete(null);
            }
        });
    }

    @Override
    public void close() {
        if(!isInitialized() || closed) return;

        try {
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

            dataCache.clear();
            identifierCacheByUuid.clear();
            identifierCacheByGameUuid.clear();
            identifierCacheByGameName.clear();

            closed = true;
        }
    }

    void syncPlayer(Player player) {
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

    void quit(Player player) {
        Identifier identifier = identifierCacheByGameName.remove(player.getName().toLowerCase());
        if(identifier == null) return;

        identifierCacheByUuid.remove(identifier.uuid());
        identifierCacheByGameUuid.remove(identifier.gameUuid());

        RemoteDataContainer container = dataCache.get(identifier);
        if(container == null) return;
        container.temporary = true;
        container.lastAccess = System.currentTimeMillis();
    }

    void quit(Identifier identifier) {
        RemoteDataContainer container = dataCache.remove(identifier);

        identifierCacheByUuid.remove(identifier.uuid());
        identifierCacheByGameUuid.remove(identifier.gameUuid());
        identifierCacheByGameName.remove(identifier.gameName().toLowerCase());

        if(container == null) return;
        container.close();
    }

    void trackingStatus(UUID uuid, boolean status) {
        if(closed || !initialized) return;

        executorService.execute(() -> clientSocket.send(
                new JSONObject()
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

    private CachedRequest findCachedRequest(UUID uuid, UUID gameUuid, String gameName, boolean delete) {
        UUID requestUuid = null;

        if(uuid != null) requestUuid = delete
                ? requestedUuidToRequestUuidCache.remove(uuid)
                : requestedUuidToRequestUuidCache.get(uuid);

        if(requestUuid == null && gameUuid != null) requestUuid = delete
                ? requestedGameUuidToRequestUuidCache.remove(gameUuid)
                : requestedGameUuidToRequestUuidCache.get(gameUuid);

        if(requestUuid == null && gameName != null) requestUuid = delete
                ? requestedGameNameToRequestUuidCache.remove(gameName.toLowerCase())
                : requestedGameNameToRequestUuidCache.get(gameName.toLowerCase());

        if(requestUuid == null) return null;

        return delete ? requestCache.remove(requestUuid) : requestCache.get(requestUuid);
    }

    private RemoteDataContainer findCachedData(UUID uuid, UUID gameUuid, String gameName) {
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
        if(closed || (uuid == null && gameUuid == null && gameName == null))
            return CompletableFuture.completedFuture(null);

        RemoteDataContainer cachedData = findCachedData(uuid, gameUuid, gameName);
        if(cachedData != null) {
            if(cachedData.temporary && cache)
                cachedData.temporary = false;
            return CompletableFuture.completedFuture(cachedData);
        }

        CachedRequest cachedRequest = findCachedRequest(uuid, gameUuid, gameName, false);
        if(cachedRequest != null && !cachedRequest.request().isDone()) return cachedRequest.request();

        if(!requestIfNotCached) return CompletableFuture.completedFuture(null);

        UUID requestUuid = UUID.randomUUID();
        CompletableFuture<RemoteDataContainer> future = openFuture.thenCompose(_ -> {
            executorService.execute(() -> createViewRequest(uuid, gameUuid, gameName));
            return new CompletableFuture<>();
        });
        future.whenComplete((_, _) -> requestCache.remove(requestUuid));

        requestCache.put(requestUuid, new CachedRequest(future, cache));

        Task.runGlobally(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("View container request timed out"));
                requestCache.remove(requestUuid);
                if (uuid != null) requestedUuidToRequestUuidCache.remove(uuid);
                if (gameUuid != null) requestedGameUuidToRequestUuidCache.remove(gameUuid);
                if (gameName != null) requestedGameNameToRequestUuidCache.remove(gameName.toLowerCase());
            }
        }, 20L * REQUEST_TIMEOUT_SECONDS);

        if(uuid != null) requestedUuidToRequestUuidCache.put(uuid, requestUuid);
        if(gameUuid != null) requestedGameUuidToRequestUuidCache.put(gameUuid, requestUuid);
        if(gameName != null) requestedGameNameToRequestUuidCache.put(gameName.toLowerCase(), requestUuid);

        return future;
    }

    void pushValueAsync(Identifier identifier, String field, Object value) {
        executorService.execute(() -> {
            JSONObject messageBody = new JSONObject()
                    .put("type", "storage_data_push")
                    .put(
                            "identifier",
                            new JSONObject()
                                    .put("uuid", identifier.uuid().toString())
                    )
                    .put("field", field);
            if(value != null) messageBody.put("value", value);

            clientSocket.send(messageBody.toString());
        });
    }

    private void createViewRequest(UUID uuid, UUID gameUuid, String gameName) {
        JSONObject requestBody = new JSONObject()
                .put("type", "view_container");

        JSONObject identifierBody = new JSONObject();
        if(uuid != null) identifierBody.put("uuid", uuid.toString());
        if(gameUuid != null) identifierBody.put("game_uuid", gameUuid.toString());
        if(gameName != null) identifierBody.put("game_name", gameName);

        requestBody.put("identifier", identifierBody);

        clientSocket.send(requestBody.toString());
    }

    private void createCompleteRequest(UUID uuid, String gameName, UUID requestUuid) {
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

        Task.runGlobally(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Identifier request timed out"));
                identifierRequests.remove(requestUuid);
            }
        }, 20L * REQUEST_TIMEOUT_SECONDS);

        return future;
    }

}
