package su.hitori.ux;

import dev.jorel.commandapi.CommandAPI;
import net.kyori.adventure.key.Key;
import su.hitori.api.Hitori;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.module.Module;
import su.hitori.api.module.ModuleDescriptor;
import su.hitori.api.module.compatibility.CompatibilityLayer;
import su.hitori.api.module.enable.EnableContext;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.advertisement.Advertisements;
import su.hitori.ux.chat.Chat;
import su.hitori.ux.chat.ChatListener;
import su.hitori.ux.chat.cmd.*;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.event.EventCommand;
import su.hitori.ux.event.EventListener;
import su.hitori.ux.event.Events;
import su.hitori.ux.nametag.NameTags;
import su.hitori.ux.nametag.NameTagsListener;
import su.hitori.ux.notification.Notifications;
import su.hitori.ux.storage.def.DefaultStorageImpl;
import su.hitori.ux.storage.def.StorageCommand;
import su.hitori.ux.storage.def.StorageListener;
import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.Storage;
import su.hitori.ux.stream.StreamCommand;
import su.hitori.ux.stream.StreamListener;
import su.hitori.ux.stream.Streams;
import su.hitori.ux.tab.Tab;
import su.hitori.ux.tab.TabListener;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class UXModule extends Module {

    private static final Logger LOGGER = LoggerFactory.instance().create(UXModule.class);

    private final AtomicReference<ModuleDescriptor> resourcePackModuleReference = new AtomicReference<>();

    private ScheduledExecutorService executorService;

    private Storage<? extends DataContainer> storage;
    private boolean thirdPartyStorage;

    private Advertisements advertisements;
    private Chat chat;
    private NameTags nameTags;
    private Tab tab;
    private Notifications notifications;
    private Events events;
    private Streams streams;

    @Override
    public void setupCompatibility(CompatibilityLayer compatibilityLayer) {
        Key key = Key.key("hitori", "resourcepack");
        compatibilityLayer.addEnableHook(key,
                () -> Hitori.instance().moduleRepository()
                        .getModule(key)
                        .ifPresent(resourcePackModuleReference::set)
        );
    }

    @Override
    public void enable(EnableContext context) {
        UXConfiguration config = new UXConfiguration(defaultConfig());
        config.reload();

        executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

        // init storage
        var storageConfig = config.storage;
        if(!storageConfig.implementation.equalsIgnoreCase("default")) thirdPartyStorage = true;
        else {
            var implementationConfig = storageConfig.defaultImplementation;

            String url, user = "sa", password = "";
            switch (implementationConfig.type) {
                case "h2" -> url = "jdbc:h2:%s".formatted(folder().resolve("data").toAbsolutePath());
                case "mysql" -> {
                    url = String.format("jdbc:mysql://%s/%s", implementationConfig.host, implementationConfig.database);
                    user = implementationConfig.user;
                    password = implementationConfig.password;
                }
                default -> {
                    LOGGER.warning("Wrong database type. Only allowed is: \"mysql\" and \"h2\". Local database (h2) will be loaded.");
                    url = "jdbc:h2:%s".formatted(folder().resolve("data").toAbsolutePath());
                }
            }

            DefaultStorageImpl storage = new DefaultStorageImpl(url, user, password, executorService);
            this.storage = storage;
            context.listeners().register(new StorageListener(
                    storage,
                    Hitori.instance().moduleRepository().isModuleExists(Key.key("hitori", "resourcepack"))
            ));
            context.commands().register(new StorageCommand(storage));
        }

        advertisements = new Advertisements();
        chat = new Chat(this);
        nameTags = new NameTags(resourcePackModuleReference);
        tab = new Tab(this);
        notifications = new Notifications();
        events = new Events(this);
        streams = new Streams(this);

        context.listeners().register(
                new TabListener(tab),
                new EventListener(events),
                new StreamListener(streams)
        );

        if(config.chat.enabled) {
            if(!context.hasEnabledBefore()) disableVanillaCommands();
            context.listeners().register(new ChatListener(this));
            context.commands().register(
                    new DirectMessageCommand(chat),
                    new OpenSharedInventoryCommand(chat),
                    new IgnoreCommand(this, true),
                    new IgnoreCommand(this, false),
                    new SpyCommand(this),
                    new ReplyCommand(chat),
                    new HelloCommand(chat)
            );
        }

        if(config.nameTags.enabled) {
            context.listeners().register(new NameTagsListener(nameTags));
            nameTags.start();
        }

        if(config.chat.gender.enabled)
            context.commands().register(new GenderCommand(this));

        context.commands().register(
                new EventCommand(events),
                new StreamCommand(this)
        );

        advertisements.start();
        tab.start();
        events.load();

        storage.addFieldsToUserScheme(
                GenderInfluencedText.GENDER_FIELD,
                Chat.CHAT_IGNORING_FIELD,
                Chat.DM_IGNORING_FIELD,
                SpyCommand.SPYING_FIELD,
                Events.HIDDEN_EVENTS_FIELD,
                Chat.SEEN_FIRST_VISIT_MESSAGE_FIELD
        );

        storage.addFieldsToServerScheme(
                Events.ACTIVE_EVENTS_FIELD,
                Streams.ONGOING_STREAMS_FIELD
        );

        context.enableHooksFuture().thenRun(() -> {
            if(thirdPartyStorage && storage == null) {
                // todo: maybe add a logic to framework to disable module manually
                LOGGER.severe("Module finished loading but Storage implementation was not installed. Module will not work normally");
                return;
            }

            storage.open(context.hasEnabledBefore());
            streams.load();
        });
    }

    private void disableVanillaCommands() {
        for (String command : List.of("msg", "m", "w", "tell")) {
            CommandAPI.unregister(command, true);
        }
    }

    @Override
    public void disable() {
        advertisements.stop();
        nameTags.stop();
        tab.stop();
        events.unload();
        storage.close();
    }

    public ExecutorService executorService() {
        return executorService;
    }

    public void installStorage(Storage<? extends DataContainer> storage) {
        if(!thirdPartyStorage || this.storage != null) return;
        this.storage = storage;
    }

    public Storage<DataContainer> storage() {
        return UnsafeUtil.cast(storage);
    }

    public Chat chat() {
        return chat;
    }

    public Tab tab() {
        return tab;
    }

    public Notifications notifications() {
        return notifications;
    }

    public Events events() {
        return events;
    }

    public Streams streams() {
        return streams;
    }

}
