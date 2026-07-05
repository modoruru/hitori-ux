package su.hitori.ux.storage.remote;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public record CachedRequest(CompletableFuture<@Nullable RemoteDataContainer> request, boolean temporary) {
}
