package su.hitori.ux.storage.api;

import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;

import javax.annotation.Nullable;

public interface DataContainer {

    Identifier identifier();

    long initialSizeInBytes();

    boolean isClosed();

    <E> @Nullable E get(DataField<E> field);

    default <E> E getOrDefault(DataField<E> field, E defaultElement) {
        E element = get(field);
        if(element != null) return element;
        return defaultElement;
    }

    <E> void set(DataField<E> field, @Nullable E value);

}
