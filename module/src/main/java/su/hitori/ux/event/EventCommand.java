package su.hitori.ux.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Task;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.util.DateArgumentType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class EventCommand {

    private EventCommand() {}

    public static LiteralCommandNode<CommandSourceStack> bootstrap(Events events) {
        Predicate<CommandSourceStack> onlyPlayer = source -> source.getSender() instanceof Player;
        Predicate<CommandSourceStack> onlyAdmin = source -> source.getSender().hasPermission("*");

        return Commands.literal("event")
                .then(Commands.literal("plan")
                        .requires(onlyAdmin)
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("description", StringArgumentType.string())
                                        .then(Commands.argument("start_time", DateArgumentType.date(Events.DATE_FORMAT, Events.ZONE_ID, true))
                                                .executes(context -> plan(events, context))))))
                .then(Commands.literal("ok")
                        .requires(onlyPlayer)
                        .then(Commands.argument("uuid", ArgumentTypes.uuid()))
                        .executes(context -> ok(events, context)))
                .then(Commands.literal("list")
                        .requires(onlyAdmin)
                        .executes(context -> list(events, context)))
                .then(Commands.literal("end")
                        .requires(onlyAdmin)
                        .then(Commands.argument("uuid", ArgumentTypes.uuid())
                                .executes(context -> end(events, context))))
                .then(Commands.literal("view")
                        .requires(onlyPlayer)
                        .then(Commands.argument("uuid", ArgumentTypes.uuid())
                                .executes(context -> view(events, context))))
                .build();
    }

    private static int plan(Events events, CommandContext<CommandSourceStack> context) {
        if(events.activeEvents().size() >= Events.MAX_EVENTS) {
            context.getSource().getSender().sendMessage(Messages.ERROR.create(UXConfiguration.I.events.alreadyPlanned));
            return 0;
        }

        String name = context.getArgument("name", String.class);
        String description = context.getArgument("description", String.class);
        ZonedDateTime date = context.getArgument("start_time", ZonedDateTime.class);

        events.planEvent(new Event(
                UUID.randomUUID(),
                name,
                description,
                date.toInstant().toEpochMilli()
        ));
        return 1;
    }

    private static int ok(Events events, CommandContext<CommandSourceStack> context) {
        Player sender = (Player) context.getSource().getSender();

        var config = UXConfiguration.I.events;
        Event event = events.getEvent(context.getArgument("uuid", UUID.class));
        if(event == null) {
            sender.sendMessage(Messages.ERROR.create(config.noEvent));
            return 0;
        }

        events.uxModule.storage().getUserDataContainer(sender).thenAccept(container -> {
            if(container == null || events.isHidden(container, event.uuid())) return;

            List<UUID> hidden = container.get(Events.HIDDEN_EVENTS_FIELD);

            if(hidden != null) hidden.removeIf(uuid -> events.getEvent(uuid) == null);
            else hidden = new ArrayList<>();

            hidden.add(event.uuid());

            container.set(Events.HIDDEN_EVENTS_FIELD, hidden);
            sender.sendMessage(Messages.INFO.create(config.hidden));
        });
        return 1;
    }

    private static int end(Events events, CommandContext<CommandSourceStack> context) {
        var config = UXConfiguration.I.events;
        Event event = events.getEvent(context.getArgument("uuid", UUID.class));
        if(event == null) {
            context.getSource().getSender().sendMessage(Messages.ERROR.create(config.noEvent));
            return 0;
        }

        events.endEvent(event);

        return 1;
    }

    private static int list(Events events, CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        var activeEvents = events.activeEvents();
        if(activeEvents.isEmpty()) {
            sender.sendMessage(Messages.ERROR.create("There's no active events!"));
            return 0;
        }

        StringBuilder builder = new StringBuilder("UUID's of events:");
        for (Event event : activeEvents) {
            builder.append("\n - \"").append(event.name()).append("\", ");
            builder.append("UUID: <click:copy_to_clipboard:").append(event.uuid().toString()).append("><yellow>[click to copy]</click>");
        }

        sender.sendMessage(Messages.INFO.create(builder.toString()));

        return 1;
    }

    private static int view(Events events, CommandContext<CommandSourceStack> context) {
        Event event = events.getEvent(context.getArgument("uuid", UUID.class));
        if(event == null) return 0;
        Task.ensureAsync(() -> events.showReminder((Player) context.getSource().getSender(), event));

        return 1;
    }

}
