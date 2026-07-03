package su.hitori.ux.storage.remote;

import org.json.JSONObject;
import org.jspecify.annotations.Nullable;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class RemoteDataContainer implements DataContainer {

    static final int RETAINING_TIME_SECONDS = 15;
    private static final Logger LOGGER = LoggerFactory.instance().create();

    private final RemoteStorage remoteStorage;

    private final Identifier identifier;
    private final Collection<DataField<?>> fields;
    private final Map<DataField<?>, Object> values;

    boolean temporary;
    long lastAccess;
    boolean closed;

    RemoteDataContainer(RemoteStorage remoteStorage, Identifier identifier, Collection<DataField<?>> fields, boolean temporary) {
        this.remoteStorage = remoteStorage;

        this.identifier = identifier;
        this.fields = fields;
        this.values = new ConcurrentHashMap<>();

        this.temporary = temporary;
        this.lastAccess = System.currentTimeMillis();
    }

    void initialize(JSONObject json) {
        if(json == null) return;

        values.clear();

        for (DataField<?> field : fields) {
            Object object = json.opt(field.name());
            if(object == null) continue;

            try {
                values.put(field, field.codec().decode(object));
            }
            catch (Exception exception) {
                StringWriter sw = new StringWriter();
                exception.printStackTrace(new PrintWriter(sw));
                LOGGER.warning(sw.toString());
            }
        }
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

    void setDirect(DataField<Object> field, @Nullable Object value) {
        values.put(field, value);
    }

    @Override
    public <E> void set(DataField<E> field, @Nullable E value) {
        if(closed) throw new IllegalStateException("DataContainer is closed");
        if(!fields.contains(field)) throw new IllegalArgumentException("Such field is not registered in scheme");

        lastAccess = System.currentTimeMillis();

        values.put(field, value);

        remoteStorage.pushValueAsync(identifier.uuid(), field.name(), value);
    }

    void close(boolean sendTrackingStatus) {
        if(closed) return;

        values.clear();

        if(sendTrackingStatus)
            remoteStorage.trackingStatus(identifier.uuid(), false);

        closed = true;
    }

}
