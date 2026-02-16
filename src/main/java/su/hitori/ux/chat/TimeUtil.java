package su.hitori.ux.chat;

import su.hitori.api.Pair;

import java.util.List;

final class TimeUtil {

    private static final long
            MILLIS_IN_SECOND = 1000L,
            MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND,
            MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE,
            MILLIS_IN_DAY = 24 * MILLIS_IN_HOUR;

    private static final List<Pair<Long, String>> TIME_UNITS = List.of(
            Pair.of(MILLIS_IN_DAY, "d"),
            Pair.of(MILLIS_IN_HOUR, "h"),
            Pair.of(MILLIS_IN_MINUTE, "m"),
            Pair.of(MILLIS_IN_SECOND, "s")
    );

    public static String formatLength(long millis) {
        if(millis <= 0) return "0s";

        StringBuilder builder = new StringBuilder();
        long remaining = millis;

        for (Pair<Long, String> timeUnit : TIME_UNITS) {
            long unitLength = timeUnit.first();
            if(remaining >= unitLength) {
                builder.append(remaining / unitLength).append(timeUnit.second()).append(' ');
                remaining %= unitLength;
            }
        }

        int length = builder.length();
        if(builder.charAt(length - 1) == ' ')
            builder.deleteCharAt(length - 1);

        return builder.toString();
    }

}
