package su.hitori.ux.placeholder;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @param <E> object for resolving placeholders
 */
public final class DynamicPlaceholder<E> {

    private final String name;
    private final Function<E, Object> supplier;

    private DynamicPlaceholder(@NotNull String name, Function<E, Object> supplier) {
        this.name = name;
        this.supplier = supplier;
    }

    public String name() {
        return name;
    }

    public Object value(E object) {
        return supplier.apply(object);
    }

    public static <E> DynamicPlaceholder<E> create(@NotNull String name, @NotNull Function<E, Object> supplier) {
        return new DynamicPlaceholder<>(name, supplier);
    }

}
