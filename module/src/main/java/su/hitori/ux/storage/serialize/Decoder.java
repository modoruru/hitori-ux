package su.hitori.ux.storage.serialize;

import java.util.function.Function;

public interface Decoder<E> extends Function<Object, E> {
}
