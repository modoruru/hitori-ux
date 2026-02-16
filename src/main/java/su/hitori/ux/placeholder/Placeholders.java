package su.hitori.ux.placeholder;

import java.util.function.Supplier;

public final class Placeholders {

    private Placeholders() {}

    private static String replace(String original, String substring, Supplier<Object> supplier) {
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int substringLength = substring.length();

        int index;
        while ((index = original.indexOf(substring, lastIndex)) != -1) {
            result.append(original, lastIndex, index);
            result.append(String.valueOf(supplier.get()));
            lastIndex = index + substringLength;
        }

        result.append(original.substring(lastIndex));

        return result.toString();
    }

    @SafeVarargs
    public static <E> String resolveDynamic(String input, E object, DynamicPlaceholder<E>... placeholders) {
        return resolveDynamic(input, object, '%', '%', placeholders);
    }

    @SafeVarargs
    public static <E> String resolveDynamic(String input, E object, Character placeholderStartChar, Character placeholderEndChar, DynamicPlaceholder<E>... placeholders) {
        if(placeholders == null) return input;

        for (DynamicPlaceholder<E> placeholder : placeholders) {
            StringBuilder builder = new StringBuilder();
            if(placeholderStartChar != null) builder.append(placeholderStartChar);
            builder.append(placeholder.name());
            if(placeholderEndChar != null) builder.append(placeholderEndChar);

            input = replace(input, builder.toString(), () -> placeholder.value(object));
        }

        return input;
    }

    public static String resolve(String input, Placeholder... placeholders) {
        return resolve(input, '%', '%', placeholders);
    }

    public static String resolve(String input, Character placeholderStartChar, Character placeholderEndChar, Placeholder... placeholders) {
        if(placeholders == null) return input;

        for (Placeholder placeholder : placeholders) {
            StringBuilder builder = new StringBuilder();
            if(placeholderStartChar != null) builder.append(placeholderStartChar);
            builder.append(placeholder.name());
            if(placeholderEndChar != null) builder.append(placeholderEndChar);

            input = replace(input, builder.toString(), placeholder::value);
        }

        return input;
    }

}
