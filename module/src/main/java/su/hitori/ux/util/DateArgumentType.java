package su.hitori.ux.util;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@NullMarked
public final class DateArgumentType implements CustomArgumentType<ZonedDateTime, String> {

    private final StringArgumentType nativeArgument;
    private final DateTimeFormatter formatter;
    private final ZoneId timeZone;
    private final boolean shouldBeInFuture;

    private DateArgumentType(DateTimeFormatter formatter, ZoneId timeZone, boolean shouldBeInFuture) {
        this.nativeArgument = StringArgumentType.string();
        this.formatter = formatter;
        this.timeZone = timeZone;
        this.shouldBeInFuture = shouldBeInFuture;
    }

    public static DateArgumentType date(DateTimeFormatter formatter, ZoneId timeZone, boolean shouldBeInFuture) {
        return new DateArgumentType(formatter, timeZone, shouldBeInFuture);
    }

    @Override
    public ZonedDateTime parse(StringReader reader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> ZonedDateTime parse(StringReader reader, S source) throws CommandSyntaxException {
        String text = nativeArgument.parse(reader);
        try {
            ZonedDateTime zonedDateTime = LocalDateTime.parse(text, formatter).atZone(timeZone);
            if(shouldBeInFuture && zonedDateTime.toInstant().isBefore(Instant.now()))
                throw new SimpleCommandExceptionType(new LiteralMessage("Date should be in future!")).create();

            return zonedDateTime;
        }
        catch (Exception exception) {
            if(exception instanceof CommandSyntaxException) throw exception;

            throw new SimpleCommandExceptionType(new LiteralMessage("Error parsing date, format is: %s".formatted(formatter.toString()))).create();
        }
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

}
