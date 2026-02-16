package su.hitori.ux.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.storage.serialize.JSONCodec;

import java.util.ArrayList;
import java.util.List;

public record DataField<E>(String name, JSONCodec<E> codec) {

    public static DataField<Long> createLong(String name) {
        return new DataField<>(name, castCodec());
    }

    public static DataField<Boolean> createBoolean(String name) {
        return new DataField<>(name, castCodec());
    }

    public static DataField<Double> createDouble(String name) {
        return new DataField<>(name, castCodec());
    }

    public static DataField<Float> createFloat(String name) {
        return new DataField<>(name, castCodec());
    }

    public static DataField<Integer> createInteger(String name) {
        return new DataField<>(name, castCodec());
    }

    public static DataField<String> createString(String name) {
        return new DataField<>(name, castCodec());
    }

    public static <E extends Enum<E>> DataField<E> createEnum(String name, Class<E> clazz) {
        return new DataField<>(name, enumCodec(clazz));
    }

    public static <E> DataField<List<E>> createTypedList(String name, JSONCodec<E> codec) {
        return new DataField<>(name, listCodec(codec));
    }

    public static <E extends Enum<E>> JSONCodec<E> enumCodec(Class<E> clazz) {
        return new JSONCodec<>(
                Enum::ordinal,
                obj -> clazz.getEnumConstants()[(int) obj]
        );
    }

    private static <E> JSONCodec<List<E>> listCodec(JSONCodec<E> codec) {
        return new JSONCodec<>(
                list -> {
                    JSONArray array = new JSONArray();
                    for (E e : list) {
                        array.put(codec.encode(e));
                    }
                    return array;
                },
                obj -> {
                    JSONArray array = (JSONArray) obj;
                    List<E> list = new ArrayList<>();
                    for (Object object : array) {
                        list.add(codec.decode(object));
                    }
                    return list;
                }
        );
    }

    @ApiStatus.Internal
    public static <E> JSONCodec<E> castCodec() {
        return new JSONCodec<>(
                obj -> obj,
                UnsafeUtil::cast
        );
    }

    @Override
    public @NotNull String toString() {
        return "DataField<>(name: \"" + name + "\")";
    }

}
