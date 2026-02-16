package su.hitori.ux.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import su.hitori.api.Pair;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.registry.RegistryAccess;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Text;
import su.hitori.ux.Sound;
import su.hitori.ux.UXModule;
import su.hitori.ux.chat.channel.ChatChannel;
import su.hitori.ux.chat.cmd.SpyCommand;
import su.hitori.ux.chat.event.AsyncChatChooseReceiversEvent;
import su.hitori.ux.chat.event.AsyncDirectMessageEvent;
import su.hitori.ux.chat.event.AsyncJoinReactionEvent;
import su.hitori.ux.chat.event.AsyncPreChatMessageEvent;
import su.hitori.ux.chat.replacement.Replacement;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.notification.NotificationType;
import su.hitori.ux.permission.DefaultPermission;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;
import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.Identifier;
import su.hitori.ux.storage.Storage;
import su.hitori.ux.storage.serialize.JSONCodec;

import java.net.URI;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Chat {

    public static final DynamicPlaceholder<Player> PLAYER_NAME_PLACEHOLDER = DynamicPlaceholder.create(
            "player_name",
            Player::getName
    );

    private static final Logger LOGGER = LoggerFactory.instance().create(Chat.class);

    private static final JSONCodec<Set<UUID>> SET_CODEC = new JSONCodec<>(
            set -> {
                JSONArray array = new JSONArray();
                for (UUID uuid : set) {
                    array.put(uuid.toString());
                }
                return array;
            },
            obj -> {
                JSONArray array = (JSONArray) obj;
                Set<UUID> set = new HashSet<>();
                for (Object object : array) {
                    set.add(UUID.fromString((String) object));
                }
                return set;
            }
    );

    private static final Pattern BOXED_URL_PATTERN = Pattern.compile("(?<prefix>.*?)\\[(?<text>.*)]\\((?<url>.*)\\)(?<suffix>.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("(?<prefix>.*?)(?<url>https?://\\S+)(?<suffix>.*)", Pattern.CASE_INSENSITIVE);

    // prefix, url, url, text, suffix
    // prefix, url, url, url, suffix
    private static final String BOXED_URL_FORMAT = "%s<click:open_url:'%s'><hover:show_text:'%s'><gray><underlined>%s</underlined></click>%s";
    private static final String URL_FORMAT = "%s<click:open_url:'%s'><hover:show_text:'%s'><gray><underlined>%s</underlined></click>%s";

    public static final DataField<Boolean>
            SEEN_FIRST_VISIT_MESSAGE_FIELD = DataField.createBoolean("seen_first_visit_message");
    public static final DataField<Set<UUID>>
            DM_IGNORING_FIELD = new DataField<>("dm_ignoring", SET_CODEC),
            CHAT_IGNORING_FIELD = new DataField<>("chat_ignoring", SET_CODEC);

    private final UXModule uxModule;
    private final Storage storage;
    private final ChatRegistries chatRegistries;

    private final Map<UUID, SharedInventoryContainer> sharedInventories;
    private final Map<Player, UUID> playerToTheirSharedInventory;

    final Map<String, String> lastDM;
    final Map<Player, Set<UUID>> seenJoinOf;

    public Chat(UXModule uxModule) {
        this.uxModule = uxModule;
        this.storage = uxModule.storage();
        this.chatRegistries = new ChatRegistries();

        this.sharedInventories = new HashMap<>();
        this.playerToTheirSharedInventory = new HashMap<>();

        this.lastDM = new HashMap<>();
        this.seenJoinOf = new HashMap<>();
    }

    public RegistryAccess registryAccess() {
        return chatRegistries;
    }

    public void sendHello(Player from, Player joined) {
        uxModule.executorService().execute(() -> sendHelloInternal(from, joined));
    }

    private void sendHelloInternal(Player from, Player joined) {
        if(from == joined) return;

        Set<UUID> seenJoin = seenJoinOf.get(from);
        if(seenJoin == null || !seenJoin.remove(joined.getUniqueId())) return;

        AsyncJoinReactionEvent event = new AsyncJoinReactionEvent(
                from,
                joined,
                UXConfiguration.I.chat.joinQuit.hello
        );
        if(!event.callEvent()) return;

        chatMessage(
                from,
                Text.create(Placeholders.resolveDynamic(
                        "!" + event.helloFormat(),
                        joined,
                        PLAYER_NAME_PLACEHOLDER
                ))
        );
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean validateURL(String url) {
        if(url == null || url.isEmpty()) return false;
        try {
            URI.create(url).toURL();
            return true;
        }
        catch (Exception _) {
            return false;
        }
    }

    public void chatMessage(Player sender, Component message) {
        String input = Text.restrictTags(Text.serialize(message).replace("\\\\", "\\"));
        if(input.isEmpty()) return;

        StringBuilder builder = new StringBuilder(input); // save input for event

        char firstCharacter = builder.charAt(0);
        ChatChannel chatChannel = null;
        for (ChatChannel registeredChannel : chatRegistries.chatChannelRegistry.elements()) {
            char channelPrefix = registeredChannel.prefixSymbol();
            if(channelPrefix != '\0' && channelPrefix == firstCharacter) {
                chatChannel = registeredChannel;
                break;
            }
        }

        if(chatChannel == null) chatChannel = chatRegistries.localChatChannel;
        else {
            builder.deleteCharAt(0);
            if(builder.isEmpty()) return;
        }

        // URL Processing
        Matcher urlMatcher = BOXED_URL_PATTERN.matcher(builder);
        if(urlMatcher.find() && validateURL(urlMatcher.group("url"))) {
            String url = urlMatcher.group("url");
            builder = new StringBuilder(String.format(
                    BOXED_URL_FORMAT,
                    urlMatcher.group("prefix"),
                    url, url,
                    urlMatcher.group("text"),
                    urlMatcher.group("suffix")
            ));
        }
        else if((urlMatcher = URL_PATTERN.matcher(builder)).find() && validateURL(urlMatcher.group("url"))) {
            String url = urlMatcher.group("url");
            builder = new StringBuilder(String.format(
                    URL_FORMAT,
                    urlMatcher.group("prefix"),
                    url, url, url,
                    urlMatcher.group("suffix")
            ));
        }

        LinkedHashSet<FormatCode> codeBuffer = new LinkedHashSet<>();
        if(DefaultPermission.CHAT_FORMATTING.hasPermission(sender)) {
            int index;
            int belowLimit = - 1;
            while ((index = builder.indexOf("&")) != -1 && index > belowLimit) {
                if(index >= builder.length() - 1) break; // we cant check if there's

                belowLimit = index;

                FormatCode code = FormatCode.INDEX.get(builder.charAt(index + 1));
                if(code == null) continue;

                if(code == FormatCode.RESET) {
                    builder.delete(index, index + 2);

                    int offsetIndex = index;
                    while (!codeBuffer.isEmpty()) {
                        String toInsert = "</" + codeBuffer.removeLast().minimessage + '>';
                        builder.insert(offsetIndex, toInsert);
                        offsetIndex += toInsert.length();
                    }
                    continue;
                }

                if(codeBuffer.contains(code)) builder.replace(index, index + 2, "");
                else {
                    codeBuffer.addLast(code);
                    builder.replace(index, index + 2, '<' + code.minimessage + '>');
                }
            }
        }

        Replacement.fillWithReplacements(chatRegistries.replacementRegistry, sender, builder);

        AsyncPreChatMessageEvent event1 = new AsyncPreChatMessageEvent(
                sender,
                input,
                chatChannel,
                builder.toString()
        );
        if(!event1.callEvent()) return;

        input = event1.formattedMessage();

        var chatConfig = UXConfiguration.I.chat;

        Set<Player> mentioned = new HashSet<>();
        if(chatConfig.mentions.enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if(player == sender) continue;
                String name = player.getName();

                if(input.contains(name)) {
                    mentioned.add(player);
                    input = input.replaceAll(
                            name,
                            Placeholders.resolveDynamic(
                                    chatConfig.mentions.formatting,
                                    player,
                                    PLAYER_NAME_PLACEHOLDER
                            )
                    );
                }
            }
        }

        String resultRaw = Placeholders.resolve(
                chatChannel.format(),
                Placeholder.create("player_name", sender::getName),
                Placeholder.createFinal("message", input)
        );

        Component result = Text.create(resultRaw);

        var localChatConfig = chatConfig.localChat;

        Storage storage = uxModule.storage();
        DataContainer senderContainer;
        try {
            senderContainer = storage.getUserDataContainer(sender).get();
        }
        catch (Throwable ex) {
            return;
        }
        if(senderContainer == null) return;

        Set<Player> receivers = chatChannel.resolveReceivers(sender, senderContainer);

        receivers.removeIf(player -> {
            if(player == sender) return false;

            try {
                Identifier receiverIdentifier = storage.getIdentifierByGameName(player.getName()).get();
                return uxModule.chat().isIgnoring(receiverIdentifier, senderContainer.identifier(), IgnoringType.CHAT);
            }
            catch (Throwable ex) {
                return false;
            }
        });

        new AsyncChatChooseReceiversEvent(
                sender,
                chatChannel,
                receivers
        ).callEvent();

        // send result message
        for (Player player : receivers) {
            if(mentioned.contains(player)) {
                var notification = chatConfig.mentions.notification;
                uxModule.notifications().sendNotification(
                        player,
                        NotificationType.MENTION,
                        Placeholders.resolve(
                                notification.text.convert().determine(senderContainer),
                                Placeholder.create("mentioner_name", sender::getName),
                                Placeholder.create("player_name", player::getName)
                        ),
                        notification.sound.convert()
                );
            }
            player.sendMessage(result);
        }

        Bukkit.getConsoleSender().sendMessage(result);

        if(chatChannel == chatRegistries.localChatChannel) {
            if (receivers.size() == 1 && localChatConfig.nobodyHeardEnabled)
                sender.sendMessage(Text.create(localChatConfig.nobodyHeard));
            sendForSpying(sender, receivers, event1.originalMessage(), input, resultRaw);
        }
    }

    private void sendForSpying(Player sender, Set<Player> receivers, String input, String formattedInput, String formattedMessage) {
        Component result = Text.create(Placeholders.resolve(
                UXConfiguration.I.chat.localChat.spying.format,
                Placeholder.create("sender_name", sender::getName),
                Placeholder.createFinal("input", input),
                Placeholder.createFinal("formatted_input", formattedInput),
                Placeholder.createFinal("formatted_message", formattedMessage)
        ));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if(receivers.contains(player)) continue;
            uxModule.storage().getUserDataContainer(player).thenAccept(container -> {
                if(container != null && container.getOrDefault(SpyCommand.SPYING_FIELD, false)) player.sendMessage(result);
            });
        }
    }

    public String getLastDM(Player sender) {
        return lastDM.get(sender.getName().toLowerCase());
    }

    public void sendDirectMessage(Player sender, Player receiver, String message) {
        var senderFuture = storage.getUserDataContainer(sender);
        storage.getUserDataContainer(receiver)
                .thenCombine(senderFuture, (receiverContainer, senderContainer) -> Pair.of(
                        Optional.ofNullable(receiverContainer),
                        Optional.ofNullable(senderContainer)
                ))
                .thenAccept(pair -> {
                    try {
                        sendDirectMessageInternal(
                                sender,
                                pair.second().orElseThrow(),
                                receiver,
                                pair.first().orElseThrow(),
                                message
                        );
                    }
                    catch (Throwable ex) {
                        LOGGER.warning(ex.getMessage());
                    }
                });
    }

    private void sendDirectMessageInternal(Player sender, DataContainer senderContainer, Player receiver, DataContainer receiverContainer, String message) {
        var directMessagesConfig = UXConfiguration.I.chat.directMessages;

        if(sender == receiver) {
            sender.sendMessage(Messages.ERROR.create(directMessagesConfig.cantSendYourself));
            return;
        }

        if(isIgnoring(senderContainer.identifier(), receiverContainer.identifier(), IgnoringType.DIRECT_MESSAGES)) {
            sender.sendActionBar(Text.create(Placeholders.resolve(
                    directMessagesConfig.haveBlocked.convert().determine(receiverContainer),
                    Placeholder.create("receiver_name", receiver::getName)
            )));
            return;
        }

        if(isIgnoring(receiverContainer.identifier(), senderContainer.identifier(), IgnoringType.DIRECT_MESSAGES)) {
            sender.sendActionBar(Text.create(Placeholders.resolve(
                    directMessagesConfig.areBlocked.convert().determine(receiverContainer),
                    Placeholder.create("receiver_name", receiver::getName)
            )));
            return;
        }

        uxModule.executorService().execute(() -> {
            AsyncDirectMessageEvent event = new AsyncDirectMessageEvent(
                    sender,
                    receiver,
                    message,
                    Text.create(Placeholders.resolve(
                            directMessagesConfig.receiverFormat,
                            Placeholder.create("sender_name", sender::getName),
                            Placeholder.create("message", () -> message)
                    )),
                    Text.create(Placeholders.resolve(
                            directMessagesConfig.senderFormat,
                            Placeholder.create("receiver_name", receiver::getName),
                            Placeholder.create("message", () -> message)
                    )),
                    directMessagesConfig.receiveSound.convert()
            );

            if(!event.callEvent()) return;

            setLastDM(sender, receiver);

            sender.sendMessage(event.senderResult());
            receiver.sendMessage(event.receiverResult());

            Sound sound = event.receiveSound();
            if(sound != null) {
                receiver.playSound(receiver, sound.name(), sound.volume(), sound.pitch());
            }
        });
    }

    private void setLastDM(Player sender, Player receiver) {
        String senderName = sender.getName().toLowerCase(), receiverName = receiver.getName().toLowerCase();
        lastDM.put(senderName, receiverName);
        lastDM.put(receiverName, senderName);
    }

    public void clearLastDMs() {
        lastDM.clear();
    }

    // shared inventories
    public void deleteSharedInventory(Player player) {
        UUID sharedInventoryUuid = playerToTheirSharedInventory.get(player);
        SharedInventoryContainer sharedInventory;

        if(sharedInventoryUuid != null && (sharedInventory = sharedInventories.remove(sharedInventoryUuid)) != null)
            sharedInventory.close();
    }

    public SharedInventoryContainer createSharedInventory(Player player) {
        deleteSharedInventory(player);

        SharedInventoryContainer sharedInventory = new SharedInventoryContainer(player);
        sharedInventories.put(sharedInventory.uuid(), sharedInventory);
        playerToTheirSharedInventory.put(player, sharedInventory.uuid());

        return sharedInventory;
    }

    public SharedInventoryContainer getSharedInventory(UUID uuid) {
        return sharedInventories.get(uuid);
    }

    public Set<Identifier> resolveIgnoringSetIdentifiers(Identifier identifier, IgnoringType ignoringType) {
        Set<Identifier> identifiers = new HashSet<>();
        for (UUID uuid : getIgnoringSet(identifier, ignoringType)) {
            try {
                identifiers.add(storage.getIdentifierByUUID(uuid).get());
            }
            catch (Throwable _) {
            }
        }
        return identifiers;
    }

    // ignore
    public Set<UUID> getIgnoringSet(Identifier identifier, IgnoringType ignoringType) {
        return Set.copyOf(getIgnoringSet(identifier, ignoringType.field));
    }

    public boolean isIgnoring(Identifier identifier, Identifier toCheck, IgnoringType ignoringType) {
        return getIgnoringSet(identifier, ignoringType).contains(toCheck.uuid());
    }

    public void setIgnoring(Identifier identifier, Identifier toIgnore, IgnoringType ignoringType, boolean ignoring) {
        Set<UUID> set = getIgnoringSet(identifier, ignoringType.field);

        if(set.contains(toIgnore.uuid()) == ignoring) return;

        if(ignoring) set.add(toIgnore.uuid());
        else set.remove(toIgnore.uuid());

        setSet(identifier, ignoringType.field, set);
    }

    private Set<UUID> getIgnoringSet(Identifier identifier, DataField<Set<UUID>> field) {
        return getDataContainer(identifier)
                .map(container -> container.get(field))
                .orElse(new HashSet<>());
    }

    private void setSet(Identifier identifier, DataField<Set<UUID>> field, Set<UUID> value) {
        getDataContainer(identifier).ifPresent(container ->
                container.set(field, value)
        );
    }

    private Optional<DataContainer> getDataContainer(Identifier identifier) {
        try {
            return Optional.ofNullable(storage.getUserDataContainer(identifier, true, false).get());
        }
        catch (Throwable ex) {
            return Optional.empty();
        }
    }

    private enum FormatCode {
        RESET('r', null),

        BOLD('l', "b"),
        ITALIC('o', "italic"),
        UNDERLINED('n', "u"),
        STRIKETHROUGH('m', "st"),
        OBFUSCATED('k', "obf"),

        BLACK('0', "black"),
        DARK_BLUE('1', "dark_blue"),
        DARK_GREEN('2', "dark_green"),
        DARK_AQUA('3', "dark_aqua"),
        DARK_RED('4', "dark_red"),
        DARK_PURPLE('5', "dark_purple"),
        GOLD('6', "gold"),
        GRAY('7', "gray"),
        DARK_GRAY('8', "dark_gray"),
        BLUE('9', "blue"),
        GREEN('a', "green"),
        AQUA('b', "aqua"),
        RED('c', "red"),
        LIGHT_PURPLE('d', "light_purple"),
        YELLOW('e', "yellow");

        static final Map<Character, FormatCode> INDEX;

        static {
            INDEX = new HashMap<>();
            for (FormatCode value : values()) {
                INDEX.put(value.sym, value);
            }
        }

        final char sym;
        final String minimessage;

        FormatCode(char sym, String minimessage) {
            this.sym = sym;
            this.minimessage= minimessage;
        }

    }

}
