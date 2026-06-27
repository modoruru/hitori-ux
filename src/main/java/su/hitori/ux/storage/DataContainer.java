package su.hitori.ux.storage;

import javax.annotation.Nullable;

/**
 * DataContainer is a per-player container with their additional data stored.
 * Depending on implementation, data is temporarily stored in cache or queried/inserted directly into the database.
 */
public interface DataContainer {

    /**
     * Identifier of container
     */
    Identifier identifier();

    /**
     * Size this container weighed when it was initialized
     * @return size in bytes
     */
    long initialSizeInBytes();

    /**
     * Is this container is already closed. If true, container will throw an exception on any attempt to manipulate with data.
     * @return is this container is closed
     */
    boolean isClosed();

    /**
     * Returns value from specified field.
     * <p></p>
     * Some storage implementations require to register fields at startup using the {@link Storage#addFieldsToUserScheme(DataField[])} method before using those fields.
     * @param field field to read
     * @return value stored under this field. can be null.
     * @param <E> type of value
     */
    <E> @Nullable E get(DataField<E> field);

    /**
     * Returns value from specified field.
     * <p></p>
     * Some storage implementations require to register fields at startup using the {@link Storage#addFieldsToUserScheme(DataField[])} method before using those fields.
     * @param field field to read
     * @param defaultElement the value to return if the stored value is null
     * @return value stored under this field. will return default value if the stored value is null.
     * @param <E> type of value
     */
    default <E> E getOrDefault(DataField<E> field, E defaultElement) {
        E element = get(field);
        if(element != null) return element;
        return defaultElement;
    }

    /**
     * Writes value under specified field
     * <p></p>
     * Some storage implementations require to register fields at startup using the {@link Storage#addFieldsToUserScheme(DataField[])} method before using those fields.
     * @param field field to write to
     * @param value value to write to the container
     * @param <E> type of value
     */
    <E> void set(DataField<E> field, @Nullable E value);

}
