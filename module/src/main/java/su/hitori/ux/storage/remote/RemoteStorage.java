package su.hitori.ux.storage.remote;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.Either;
import su.hitori.api.util.Task;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;
import su.hitori.ux.storage.remote.client.ClientSocket;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class RemoteStorage implements Storage<RemoteDataContainer> {

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

    private final Map<Identifier, CompletableFuture<@Nullable RemoteDataContainer>> requestCache;
    private final Map<Identifier, RemoteDataContainer> dataCache;
    private final Map<String, Identifier> identifierCache;
    private final CompletableFuture<Void> openFuture;

    final Map<String, DataField<?>> userDataScheme;
    final Map<String, DataField<?>> serverDataScheme;

    private boolean initialized;
    private boolean closed;
    private Task saveTask;
    private Task removeTemporaryTask;

    public RemoteStorage(ExecutorService executorService, URI uri, String user, String password) {
        this.executorService = executorService;
        this.clientSocket = new ClientSocket(uri, this::handleMessage);
        this.user = user;
        this.password = password;

        this.requestCache = new ConcurrentHashMap<>();
        this.dataCache = new ConcurrentHashMap<>();
        this.identifierCache = new ConcurrentHashMap<>();
        this.openFuture = new CompletableFuture<>();

        this.userDataScheme = new HashMap<>();
        this.serverDataScheme = new HashMap<>();
    }

    // メソッドをオーバーライドする可能性を残しておく
    protected void handleMessage(JSONObject messageBody) {
        switch (messageBody.optString("type", "").toLowerCase()) {
            case "storage_connect" -> {
                if(initialized) break;

                boolean success = messageBody.optBoolean("success", false);
                if(success) {
                    openFuture.complete(null);
                    LOGGER.info("RemoteStorage initialized!");
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
                var request = requestCache.get(identifier);
                if(request == null) {
                    LOGGER.warning(String.format(
                            "Received view_container message for [uuid: %s, game_uuid: %s, game_name: %s] without requesting it :)",
                            identifier.uuid(),
                            identifier.gameUuid(),
                            identifier.gameName()
                    ));
                    return;
                }


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

        CompletableFuture.supplyAsync(() -> {
            try {
                if(!clientSocket.connectBlocking()) {
                    LOGGER.warning("Unable to connect to RemoteStorage.");
                    throw new IllegalStateException();
                }

                clientSocket.send(
                        new JSONObject()
                                .put("type", "storage_connect")
                                .put("user", user)
                                .put("password", password)
                                .toString()
                );
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

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        if(isInitialized()) return CompletableFuture.supplyAsync(supplier, executorService);
        else if(closed) return CompletableFuture.completedFuture(null);
        return openFuture.thenCompose(_ -> CompletableFuture.supplyAsync(supplier, executorService));
    }

    @Override
    public void close() {
        if(!isInitialized() || closed) return;

        closed = true;
    }

    @Override
    public CompletableFuture<Set<Identifier>> getAllIdentifiers() {
        return null;
    }

    @Override
    public Player getPlayerByIdentifier(Identifier identifier) {
        return Bukkit.getPlayer(identifier.gameName());
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getServerDataContainer() {
        return getUserDataContainer(SERVER_DATA_IDENTIFIER, true, true);
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getUserDataContainer(Identifier identifier, boolean requestIfNotCached, boolean cache) {
        if(closed || identifier == null)
            return CompletableFuture.completedFuture(null);

        var cachedData = dataCache.get(identifier);
        if(cachedData != null) {
            if(cachedData.temporary && cache)
                cachedData.temporary = false;
            return CompletableFuture.completedFuture(cachedData);
        }

        var cachedFuture = requestCache.get(identifier);
        if(cachedFuture != null && !cachedFuture.isDone()) return cachedFuture;

        if(!requestIfNotCached) return CompletableFuture.completedFuture(null);

        return requestCache.computeIfAbsent(identifier, key -> {
            CompletableFuture<RemoteDataContainer> future = openFuture.thenCompose(_ -> {
                createViewRequest(identifier.uuid(), identifier.gameUuid(), identifier.gameName());
                return new CompletableFuture<>();
            });

            future.whenComplete((_, _) -> requestCache.remove(key, future));

            return future;
        });
    }

    public CompletableFuture<RemoteDataContainer> getUserDataContainer(Either<UUID, String> uuidOrGameName, boolean requestIfNotCached, boolean cache) {

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

    @Override
    public CompletableFuture<@Nullable Identifier> getIdentifier(Either<UUID, String> uuidOrGameName) {
        return null;
    }

}
