package su.hitori.ux.storage;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.storage.serialize.JSONCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class DataContainer {

    static final int RETAINING_TIME_SECONDS = 15;

    private static final Set<Class<?>> JSON_GENERICS = Set.of(
            JSONArray.class, JSONObject.class,
            Long.class, long.class,
            Boolean.class, boolean.class,
            Double.class, double.class,
            Float.class, float.class,
            Integer.class, int.class,
            String.class
    );

    private final Identifier identifier;
    private final BiConsumer<DataContainer, Boolean> saveFunction;
    private final Set<DataField<?>> fields;
    private final long initialSizeInBytes;

    boolean temporary;
    long lastAccess;

    private final Map<DataField<?>, Object> values;
    boolean fresh;
    private boolean closed;

    DataContainer(Identifier identifier, BiConsumer<DataContainer, Boolean> saveFunction, Set<DataField<?>> fields, long initialSizeInBytes, boolean temporary, JSONObject json) {
        this.identifier = identifier;
        this.saveFunction = saveFunction;
        this.fields = fields;
        this.initialSizeInBytes = initialSizeInBytes;
        this.temporary = temporary;
        this.lastAccess = System.currentTimeMillis();

        this.values = new HashMap<>();
        this.fresh = json == null;

        initialize(json);
    }

    void initialize(JSONObject json) {
        if(json == null) return;

        values.clear();

        for (DataField<?> field : fields) {
            Object object = json.opt(field.name());
            if(object == null) continue;

            values.put(field, field.codec().decode(object));
        }
    }

    public Identifier identifier() {
        return identifier;
    }

    public long initialSizeInBytes() {
        return initialSizeInBytes;
    }

    JSONObject encode() {
        if(values.isEmpty()) return null;

        JSONObject json = new JSONObject();
        for (DataField<?> field : fields) {
            Object object = values.get(field);
            if(object == null) continue;

            Object encoded = UnsafeUtil.<JSONCodec<Object>>cast(field.codec()).encode(object);
            Class<?> type = encoded.getClass();

            if(!JSON_GENERICS.contains(type)) {
                new RuntimeException("Encoded object for field " + field + " returns a non-generic type: " + type.getSimpleName()).printStackTrace();
                continue;
            }

            json.put(field.name(), encoded);
        }

        return json;
    }

    public void save() {
        if(closed) return;
        saveFunction.accept(this, false);
    }

    void close() {
        if(closed) return;

        save();
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public <E> @Nullable E get(DataField<E> field) {
        if(closed) throw new IllegalStateException("DataView is closed");
        if(!fields.contains(field)) throw new IllegalArgumentException("Such field is not registered in scheme");

        lastAccess = System.currentTimeMillis();

        return UnsafeUtil.cast(values.get(field));
    }

    public <E> E getOrDefault(DataField<E> field, E defaultElement) {
        E element = get(field);
        if(element != null) return element;
        return defaultElement;
    }

    public <E> void set(DataField<E> field, @Nullable E value) {
        if(closed) throw new IllegalStateException("DataView is closed");
        if(!fields.contains(field)) throw new IllegalArgumentException("Such field is not registered in scheme");

        lastAccess = System.currentTimeMillis();

        values.put(field, value);

        if(temporary)
            saveFunction.accept(this, true);
    }

}
