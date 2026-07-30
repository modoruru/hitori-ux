package su.hitori.ux.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.net.URL;

@NullMarked
public final class URLArgumentType implements CustomArgumentType<URL, String> {

    private final StringArgumentType nativeArgument = StringArgumentType.string();

    private URLArgumentType() {}

    public static URLArgumentType url() {
        return new URLArgumentType();
    }

    @Override
    public URL parse(StringReader reader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> URL parse(StringReader reader, S source) throws CommandSyntaxException {
        String text = nativeArgument.parse(reader);
        try {
            return URI.create(text).toURL();
        }
        catch (Throwable ex) {
            throw new SimpleCommandExceptionType(() -> "Malformed URL!").create();
        }
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return nativeArgument;
    }

}
