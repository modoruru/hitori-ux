package su.hitori.ux.storage.remote;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.Either;
import su.hitori.api.util.Task;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;
import su.hitori.ux.storage.remote.client.ClientSocket;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
    protected final String id;
    protected final String secret;

    private final Map<Identifier, CompletableFuture<@Nullable RemoteDataContainer>> requestCache;
    private final Map<Identifier, RemoteDataContainer> dataCache;
    private final Map<String, Identifier> identifierCache;
    private final CompletableFuture<Void> openFuture;

    final Set<DataField<?>> userDataScheme;
    final Set<DataField<?>> serverDataScheme;

    private boolean initialized;
    private boolean closed;
    private Task saveTask;
    private Task removeTemporaryTask;

    public RemoteStorage(ExecutorService executorService, URI uri, String id, String secret) {
        this.executorService = executorService;
        this.clientSocket = new ClientSocket(uri, this::handleMessage);
        this.id = id;
        this.secret = secret;

        this.requestCache = new ConcurrentHashMap<>();
        this.dataCache = new ConcurrentHashMap<>();
        this.identifierCache = new ConcurrentHashMap<>();
        this.openFuture = new CompletableFuture<>();

        this.userDataScheme = new HashSet<>();
        this.serverDataScheme = new HashSet<>();
    }

    // メソッドをオーバーライドする可能性を残しておく
    protected void handleMessage(JSONObject messageBody) {
        switch (messageBody.optString("type", "").toLowerCase()) {
            case "connect_storage" -> {
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
                }
            }
            case "storage_data_push" -> {

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

        (userScheme ? userDataScheme : serverDataScheme).addAll(Arrays.asList(fields));
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
                                .put("type", "connect_storage")
                                .put("id", id)
                                .put("secret", secret)
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
        return null;
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getServerDataContainer() {
        return null;
    }

    @Override
    public CompletableFuture<RemoteDataContainer> getUserDataContainer(Identifier identifier, boolean requestIfNotCached, boolean cache) {
        return null;
    }

    @Override
    public CompletableFuture<@Nullable Identifier> getIdentifier(Either<UUID, String> uuidOrGameName) {
        return null;
    }

}
