package su.hitori.ux.storage.remote;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.Either;
import su.hitori.api.util.Task;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;
import su.hitori.ux.storage.def.DefaultDataContainerImpl;
import su.hitori.ux.storage.def.DefaultStorageImpl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public final class RemoteStorage implements Storage<RemoteDataContainer> {

    private static final Identifier SERVER_DATA_IDENTIFIER = new Identifier(
            new UUID(0, 0),
            new UUID(0, 0),
            ""
    );

    private static final Logger LOGGER = LoggerFactory.instance().create(DefaultStorageImpl.class);

    private final ExecutorService executorService;

    private final Map<Identifier, CompletableFuture<@Nullable DefaultDataContainerImpl>> requestCache;
    private final Map<Identifier, DefaultDataContainerImpl> dataCache;
    private final Map<String, Identifier> identifierCache;
    private final CompletableFuture<Void> openFuture;

    final Set<DataField<?>> userDataScheme;
    final Set<DataField<?>> serverDataScheme;

    private boolean closed;
    private Task saveTask;
    private Task removeTemporaryTask;

    public RemoteStorage(ExecutorService executorService) {
        this.executorService = executorService;
        this.requestCache = new ConcurrentHashMap<>();
        this.dataCache = new ConcurrentHashMap<>();
        this.identifierCache = new ConcurrentHashMap<>();
        this.openFuture = new CompletableFuture<>();

        this.userDataScheme = new HashSet<>();
        this.serverDataScheme = new HashSet<>();
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
