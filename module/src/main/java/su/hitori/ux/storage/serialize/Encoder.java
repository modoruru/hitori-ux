package su.hitori.ux.storage.serialize;

import java.util.function.Function;

public interface Encoder<E> extends Function<E, Object> {
}
