package su.hitori.ux.storage.remote;

import org.jspecify.annotations.Nullable;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RemoteDataContainer implements DataContainer {

    private final Identifier identifier;
    private final Set<DataField<?>> fields;
    private final Map<DataField<?>, Object> values;

    boolean temporary;
    long lastAccess;
    boolean closed;

    RemoteDataContainer(Identifier identifier, Set<DataField<?>> fields, boolean temporary) {
        this.identifier = identifier;
        this.fields = fields;
        this.values = new ConcurrentHashMap<>();

        this.temporary = temporary;
        this.lastAccess = System.currentTimeMillis();
    }

    @Override
    public Identifier identifier() {
        return identifier;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public @Nullable <E> E get(DataField<E> field) {
        if(closed) throw new IllegalStateException("DataContainer is closed");
        if(!fields.contains(field)) throw new IllegalArgumentException("Such field is not registered in scheme");

        lastAccess = System.currentTimeMillis();

        return UnsafeUtil.cast(values.get(field));
    }

    @Override
    public <E> void set(DataField<E> field, @Nullable E value) {
        if(closed) throw new IllegalStateException("DataContainer is closed");
        if(!fields.contains(field)) throw new IllegalArgumentException("Such field is not registered in scheme");

        lastAccess = System.currentTimeMillis();

        values.put(field, value);

        // todo: update value on remote
    }

}
