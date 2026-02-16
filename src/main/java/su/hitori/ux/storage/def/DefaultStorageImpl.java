package su.hitori.ux.storage.def;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.Either;
import su.hitori.api.util.Task;
import su.hitori.api.util.Text;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class DefaultStorageImpl implements Storage<DefaultDataContainerImpl> {

    public boolean debug = false;

    private static final long SAVE_PERIOD = 60 * 20L; // save every 3 seconds

    private static final Identifier SERVER_DATA_IDENTIFIER = new Identifier(
            new UUID(0, 0),
            new UUID(0, 0),
            ""
    );

    private static final Logger LOGGER = LoggerFactory.instance().create(DefaultStorageImpl.class);

    private final BiConsumer<DefaultDataContainerImpl, Boolean> SAVE_FUNCTION = this::save;

    private final String url, user, password;
    private final ExecutorService executorService;

    private final SQLHandle sqlHandle;
    private final Map<Identifier, CompletableFuture<@Nullable DefaultDataContainerImpl>> requestCache;
    private final Map<Identifier, DefaultDataContainerImpl> dataCache;
    private final Map<String, Identifier> identifierCache;
    private final CompletableFuture<Void> openFuture;

    private final Set<DataField<?>> userDataScheme;
    private final Set<DataField<?>> serverDataScheme;

    private boolean closed;
    private Task saveTask;
    private Task removeTemporaryTask;

    public DefaultStorageImpl(String url, String user, String password, ScheduledExecutorService executorService) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.executorService = executorService;

        this.sqlHandle = new SQLHandle();
        this.requestCache = new ConcurrentHashMap<>();
        this.dataCache = new ConcurrentHashMap<>();
        this.identifierCache = new ConcurrentHashMap<>();
        this.openFuture = new CompletableFuture<>();

        this.userDataScheme = new HashSet<>();
        this.serverDataScheme = new HashSet<>();
    }

    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    public void addFieldsToUserScheme(DataField<?>... fields) {
        if(!isInitialized()) {
            for (DataField<?> field : fields) {
                userDataScheme.add(field);
            }
            return;
        }

        // TODO: check if this is not just late call. maybe check fields are registered by modules?

        LOGGER.warning("Late call for scheme change is made! This probably happens when another module adding fields after reload. They will be reinitialized.");
        // if module is requested another add - its probably because module reloaded and ux module is not
        // well reinitialize this fields

        Set<DataField<?>> fieldsSet = new HashSet<>(Arrays.asList(fields));
        Set<String> fieldsNames = fieldsSet.parallelStream().map(DataField::name).collect(Collectors.toSet());

        // save their current state
        Map<Identifier, JSONObject> json = new HashMap<>();
        for (DefaultDataContainerImpl value : dataCache.values()) {
            json.put(value.identifier(), value.encode());
        }

        // change scheme - set of fields are always synced with DataContainer
        userDataScheme.removeIf(field -> fieldsNames.contains(field.name()));
        userDataScheme.addAll(fieldsSet);

        // reinitialize them - all objects will get new instances with probably new classes
        for (DefaultDataContainerImpl value : dataCache.values()) {
            value.initialize(json.get(value.identifier()));
        }
    }

    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    public void addFieldsToServerScheme(DataField<?>... fields) {
        if(isInitialized()) return;
        for (DataField<?> field : fields) {
            serverDataScheme.add(field);
        }
    }

    public boolean isInitialized() {
        return openFuture.isDone();
    }

    @Override
    public void open(boolean syncAllPlayers) {
        if(isInitialized()) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                sqlHandle.open(url, user, password, null);

                try (Statement index = sqlHandle.createStatement(); Statement users = sqlHandle.createStatement()) {
                    index.execute("CREATE TABLE IF NOT EXISTS `index` (uuid VARCHAR(255) PRIMARY KEY, game_uuid VARCHAR(255), game_name VARCHAR(255))");
                    users.execute("CREATE TABLE IF NOT EXISTS users (uuid VARCHAR(255) PRIMARY KEY, body LONGTEXT)");
                }

                try (PreparedStatement statement = sqlHandle.prepareStatement("SELECT game_uuid FROM `index` WHERE uuid = ?")){
                    statement.setString(1, SERVER_DATA_IDENTIFIER.uuid().toString());
                    ResultSet set = statement.executeQuery();
                    if(!set.next()) insertIdentifier(SERVER_DATA_IDENTIFIER);
                }

                if(syncAllPlayers) {
                    Bukkit.getOnlinePlayers().forEach(player -> syncPlayer(player, false));
                }

                LOGGER.info(syncAllPlayers ? "StorageV2 initialized, but this is reload, so all players are synced also!" : "StorageV2 initialized!");

                saveTask = Task.runTaskTimerGlobally(() -> executorService.execute(this::saveAll), 0L, SAVE_PERIOD);
                removeTemporaryTask = Task.runTaskTimerGlobally(() -> executorService.execute(() -> {

                    Set<Identifier> toClose = new HashSet<>();
                    for (DefaultDataContainerImpl container : dataCache.values()) {
                        if(container.temporary && System.currentTimeMillis() > (container.lastAccess + DefaultDataContainerImpl.RETAINING_TIME_SECONDS * 1000L)) {
                            toClose.add(container.identifier());
                            LOGGER.warning(container.identifier() + " is temp and not been accessed in last 15 seconds - closing it");
                        }
                    }
                    toClose.forEach(this::quit);

                }), 0L, 20L);

                return null;
            }
            catch (SQLException e) {
                throw new RuntimeException("Got a problem while loading database.", e);
            }
        }, executorService).whenComplete((_, error) -> {
            if(error != null)
                closed = true;
            openFuture.complete(null);
        });
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        if(isInitialized()) return CompletableFuture.supplyAsync(supplier, executorService);
        else if(closed) return CompletableFuture.completedFuture(null);
        return openFuture.thenCompose(_ -> CompletableFuture.supplyAsync(supplier, executorService));
    }

    @SuppressWarnings("CallToPrintStackTrace") // we should close ignoring all the errors - shutdown should be as smooth as possible
    public void close() {
        if(!isInitialized() || closed) return;

        requestCache.clear();
        dataCache.values().forEach(DefaultDataContainerImpl::close);
        dataCache.clear();
        identifierCache.clear();

        try {
            sqlHandle.close();
        }
        catch (Throwable ex) {
            new RuntimeException("Database caused an exception while closing.", ex).printStackTrace();
            // proceed closing even if database caused an exception
        }
        finally {
            saveTask.cancel();
            saveTask = null;

            removeTemporaryTask.cancel();
            removeTemporaryTask = null;

            userDataScheme.clear();
            serverDataScheme.clear();

            closed = true;
        }
    }

    /**
     * syncs player data - loads it into cache while player online
     */
    void syncPlayer(Player player, boolean callEvent) {
        long start = System.currentTimeMillis();
        getIdentifierByGameName(player.getName())
                .thenCompose(identifier -> getUserDataContainer(identifier, true, true))
                .thenAccept(container -> {
                    if(container == null) {
                        Task.ensureSync(() -> player.kick(Component.text("Internal problem of retrieving your data.\nNo additional info is provided.")));
                        return;
                    }

                    Identifier identifier = container.identifier();
                    if(debug)
                        LOGGER.warning("Cached identifier " + identifier);

                    identifierCache.put(identifier.gameName().toLowerCase(), identifier);

                    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                    player.sendActionBar(Text.create(String.format(
                            "Synchronized in %sms (%.2fkb) <green>✔</green>",
                            System.currentTimeMillis() - start,
                            container.initialSizeInBytes() / 1024f
                    )));

                    if(callEvent)
                        new AsyncPlayerSynchronizationEvent(player, container.identifier()).callEvent();
                });
    }

    public CompletableFuture<Set<Identifier>> getAllIdentifiers() {
        return supplyAsync(() -> {
            try (PreparedStatement statement = sqlHandle.prepareStatement("SELECT uuid, game_uuid, game_name FROM index")){
                Set<Identifier> results = new HashSet<>();
                ResultSet resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    if(uuid.equals(SERVER_DATA_IDENTIFIER.uuid())) continue;

                    results.add(new Identifier(
                            uuid,
                            UUID.fromString(resultSet.getString("game_uuid")),
                            resultSet.getString("game_name")
                    ));
                }

                return results;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public @NotNull CompletableFuture<Identifier> getIdentifierByUUID(@NotNull UUID uuid) {
        return getIdentifier(Either.ofFirst(uuid));
    }

    public @NotNull CompletableFuture<Identifier> getIdentifierByGameName(@NotNull String gameName) {
        Identifier identifier = identifierCache.get(gameName.toLowerCase());
        if(identifier != null) return CompletableFuture.completedFuture(identifier);

        return getIdentifier(Either.ofSecond(gameName));
    }

    @Override
    public Player getPlayerByIdentifier(Identifier identifier) {
        return Bukkit.getPlayer(identifier.gameName());
    }

    void createIdentifierIfAbsent(@NotNull UUID gameUuid, @NotNull String gameName) {
        getIdentifierByGameName(gameName).thenAccept(resolved -> {
            if (resolved != null) return;

            Identifier identifier = new Identifier(
                    UUID.randomUUID(),
                    gameUuid,
                    gameName
            );

            try {
                insertIdentifier(identifier);
            }
            catch (RuntimeException _) {
            }
        }).join();
    }

    public Player getPlayer(Identifier identifier) {
        return Bukkit.getPlayer(identifier.gameName());
    }

    public CompletableFuture<DefaultDataContainerImpl> getServerDataContainer() {
        return getUserDataContainer(SERVER_DATA_IDENTIFIER, true, true);
    }

    public CompletableFuture<@Nullable DefaultDataContainerImpl> getUserDataContainer(Player player) {
        return getIdentifierByGameName(player.getName()).thenCompose(identifier -> getUserDataContainer(identifier, true, false));
    }

    public CompletableFuture<@Nullable DefaultDataContainerImpl> getUserDataContainer(Identifier identifier, boolean requestIfNotCached, boolean cache) {
        if(identifier == null) {
            if(debug)
                LOGGER.warning("returned null container because id was null");
            return CompletableFuture.completedFuture(null);
        }

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
            var future = supplyAsync(() -> queryDataContainer(key, cache));

            future.whenComplete((either, _) -> {
                requestCache.remove(key, future);
                if(debug)
                    either.secondOptional().ifPresent(ex -> LOGGER.warning("caught an exception while querying " + identifier + ": " + ex.getMessage()));
            });

            return future.thenApply(either -> either.firstOptional().orElse(null));
        });
    }

    @Override
    public CompletableFuture<@Nullable Identifier> getIdentifier(Either<UUID, String> uuidOrGameName) {
        String gameName = uuidOrGameName.secondOptional().orElse(null);

        Identifier cachedIdentifier;
        if(gameName != null && (cachedIdentifier = identifierCache.get(gameName.toLowerCase())) != null)
            return CompletableFuture.completedFuture(cachedIdentifier);

        return supplyAsync(() -> queryIdentifier(uuidOrGameName.firstOptional().orElse(null), gameName))
                .thenApply(
                        either -> either.firstOptional()
                                .orElse(Optional.empty())
                                .orElse(null)
                );
    }

    private Either<Optional<Identifier>, Throwable> queryIdentifier(UUID uuid, String gameName) {
        boolean uuidPresent = uuid != null;

        try (PreparedStatement statement = sqlHandle.prepareStatement(String.format(
                "SELECT %s FROM `index` WHERE %s = %s",
                uuidPresent ? "game_uuid, game_name" : "uuid, game_uuid",
                uuidPresent ? "uuid" : "LOWER(game_name)",
                gameName == null ? "?" : "LOWER(?)"
        ))) {
            statement.setString(1, uuidPresent ? uuid.toString() : gameName);
            ResultSet set = statement.executeQuery();
            if(!set.next()) return Either.ofFirst(Optional.empty());

            return Either.ofFirst(Optional.of(new Identifier(
                    uuid == null ? UUID.fromString(set.getString("uuid")) : uuid,
                    UUID.fromString(set.getString("game_uuid")),
                    gameName == null ? set.getString("game_name") : gameName
            )));
        }
        catch (Throwable ex) {
            return Either.ofSecond(ex);
        }
    }

    private Either<DefaultDataContainerImpl, Throwable> queryDataContainer(Identifier identifier, boolean cache) {
        try(PreparedStatement statement = sqlHandle.prepareStatement("SELECT body FROM users WHERE uuid = ?")) {
            boolean isServer = identifier.equals(SERVER_DATA_IDENTIFIER);

            statement.setString(1, identifier.uuid().toString());
            ResultSet set = statement.executeQuery();
            if(debug)
                LOGGER.warning("QUERY DATA CONTAINER: " + identifier);

            JSONObject json = null;
            long bytesSize = 0;
            if(set.next()) {
                String rawBody = set.getString("body");

                if(rawBody == null || rawBody.isEmpty()) json = new JSONObject();
                else {
                    json = new JSONObject(rawBody);
                    bytesSize = rawBody.getBytes().length;
                }
            }

            DefaultDataContainerImpl container = new DefaultDataContainerImpl(identifier, SAVE_FUNCTION, isServer ? serverDataScheme : userDataScheme, bytesSize, !cache, json);
            this.dataCache.put(identifier, container);
            return Either.ofFirst(container);
        }
        catch (Throwable ex) {
            return Either.ofSecond(ex);
        }
    }

    void updateIdentifier(UUID uuid, UUID newGameUuid, String newGameName) {
        try (PreparedStatement statement = sqlHandle.prepareStatement("UPDATE `index` SET game_uuid = ?, game_name = ? WHERE uuid = ?")) {
            statement.setString(1, newGameUuid.toString());
            statement.setString(2, newGameName);
            statement.setString(3, uuid.toString());
            statement.execute();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void insertIdentifier(Identifier identifier) throws RuntimeException {
        if(debug)
            LOGGER.warning("INSERT IDENTIFIER: " + identifier);
        try (PreparedStatement statement = sqlHandle.prepareStatement("INSERT INTO `index` (uuid, game_uuid, game_name) VALUES (?, ?, ?)")) {
            statement.setString(1, identifier.uuid().toString());
            statement.setString(2, identifier.gameUuid().toString());
            statement.setString(3, identifier.gameName());
            statement.execute();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if(debug)
            LOGGER.warning("INSERT IDENTIFIER END: " + identifier);
    }

    void save(DefaultDataContainerImpl container, boolean async) {
        if(async) executorService.execute(() -> saveInternal(container));
        else saveInternal(container);
    }

    void saveInternal(DefaultDataContainerImpl container) {
        JSONObject json = container.encode();
        if(json == null) json = new JSONObject();

        Identifier identifier = container.identifier();
        boolean fresh = container.fresh;
        if(fresh) container.fresh = false; // reset fresh tag on container

        // save json
        try (PreparedStatement statement = sqlHandle.prepareStatement(
                fresh
                        ? "INSERT INTO users (uuid, body) VALUES (?, ?)"
                        : "UPDATE users SET body = ? WHERE uuid = ?"
        )) {
            statement.setString(fresh ? 2 : 1, json.toString());
            statement.setString(fresh ? 1 : 2, identifier.uuid().toString());
            statement.execute();
        }
        catch (SQLException e) {
            LOGGER.warning("PIZDEC: " + e.getMessage());
            return;
        }
        if(debug)
            LOGGER.warning("UPDATE DATA CONTAINER FINISHED: " + identifier);
    }

    void saveAll() {
        dataCache.values().forEach(DefaultDataContainerImpl::save);
    }

    void quit(Player player) {
        Identifier identifier = identifierCache.remove(player.getName().toLowerCase());
        if(debug)
            LOGGER.warning(player.getName() + " is quit, closing container. identifier null? " + (identifier == null));
        if(identifier == null) return; // is it right? idk test later

        DefaultDataContainerImpl container = dataCache.get(identifier);
        if(container == null) return;
        if(debug)
            LOGGER.warning(identifier + " turned temprorary.");
        container.temporary = true;
        container.lastAccess = System.currentTimeMillis();
    }

    void quit(Identifier identifier) {
        DefaultDataContainerImpl container = dataCache.remove(identifier);
        requestCache.remove(identifier);
        if(container == null) return;
        container.close();
    }

}
