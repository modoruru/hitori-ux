package su.hitori.ux.storage.remote.util;

import java.util.regex.Pattern;

public final class IDUtil {

    private static final Pattern
            NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_\\-.]+"),
            VALUE_PATTERN = Pattern.compile("[a-z0-9_\\-./]+");

    private IDUtil() {
    }

    public static boolean validKey(String key) {
        String[] unboxed = key.split(":", 2);
        return validKey(unboxed[0], unboxed[1]);
    }

    public static boolean validKey(String namespace, String value) {
        return NAMESPACE_PATTERN.matcher(namespace).find() && VALUE_PATTERN.matcher(value).find();
    }

}
