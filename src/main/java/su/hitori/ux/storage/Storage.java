package su.hitori.ux.storage;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import su.hitori.api.util.Either;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage<Container extends DataContainer> {

    /**
     * Register fields into the user scheme to later use it in {@link DataContainer}
     * @param fields fields to register
     */
    void addFieldsToUserScheme(DataField<?>... fields);

    /**
     * Register fields into the server scheme to later use it in {@link DataContainer}
     * @param fields fields to register
     */
    void addFieldsToServerScheme(DataField<?>... fields);

    boolean isInitialized();

    void open(boolean syncAllPlayers);

    void close();

    CompletableFuture<Set<Identifier>> getAllIdentifiers();

    Player getPlayerByIdentifier(Identifier identifier);

    CompletableFuture<Container> getServerDataContainer();

    default CompletableFuture<@Nullable Container> getUserDataContainer(Player player) {
        return getIdentifier(Either.ofSecond(player.getName())).thenCompose(identifier -> getUserDataContainer(identifier, true, false));
    }

    CompletableFuture<@Nullable Container> getUserDataContainer(Identifier identifier, boolean requestIfNotCached, boolean cache);

    CompletableFuture<@Nullable Identifier> getIdentifier(Either<UUID, String> uuidOrGameName);

}
