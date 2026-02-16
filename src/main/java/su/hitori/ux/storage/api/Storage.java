package su.hitori.ux.storage.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.hitori.api.util.Either;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage<Container extends DataContainer> {

    void addFieldsToUserScheme(DataField<?>... fields);

    void addFieldsToServerScheme(DataField<?>... fields);

    boolean isInitialized();

    void open(boolean syncAllPlayers);

    void close();

    CompletableFuture<Set<Identifier>> getAllIdentifiers();

    @NotNull CompletableFuture<Identifier> getIdentifierByUUID(@NotNull UUID uuid);

    @NotNull CompletableFuture<Identifier> getIdentifierByGameName(@NotNull String gameName);

    Player getPlayerByIdentifier(Identifier identifier);

    CompletableFuture<Container> getServerDataContainer();

    default CompletableFuture<@Nullable Container> getUserDataContainer(Player player) {
        return getIdentifierByGameName(player.getName()).thenCompose(identifier -> getUserDataContainer(identifier, true, false));
    }

    CompletableFuture<@Nullable Container> getUserDataContainer(Identifier identifier, boolean requestIfNotCached, boolean cache);

    CompletableFuture<@Nullable Identifier> getIdentifier(Either<UUID, String> uuidOrGameName);

}
