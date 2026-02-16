package su.hitori.ux.placeholder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class Placeholder {

    private final String name;
    private final Supplier<Object> supplier;

    private boolean resolved;
    private Object value;

    private Placeholder(@NotNull String name, Supplier<Object> supplier) {
        this.name = name;
        this.supplier = supplier;
    }

    public String name() {
        return name;
    }

    public Object value() {
        if(resolved) return value;
        resolved = true;
        return value = supplier.get();
    }

    public static Placeholder createFinal(@NotNull String name, @Nullable Object value) {
        Placeholder placeholder = new Placeholder(name, null);
        placeholder.resolved = true;
        placeholder.value = value;
        return placeholder;
    }

    public static Placeholder create(@NotNull String name, @NotNull Supplier<Object> supplier) {
        return new Placeholder(name, supplier);
    }

}
