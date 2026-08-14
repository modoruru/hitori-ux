package su.hitori.ux.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import su.hitori.api.Pair;
import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.registry.RegistryAccess;
import su.hitori.api.util.Either;
import su.hitori.api.util.Messages;
import su.hitori.api.util.Task;
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
import su.hitori.ux.notification.Notification;
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

import java.lang.reflect.Field;
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
    private final ChatRegistries chatRegistries;

    private final Map<UUID, SharedInventoryContainer> sharedInventories;
    private final Map<Player, UUID> playerToTheirSharedInventory;

    final Map<String, String> lastDM;
    final Map<UUID, Set<UUID>> seenJoinOf;

    public Chat(UXModule uxModule) {
        this.uxModule = uxModule;
        this.chatRegistries = new ChatRegistries();

        this.sharedInventories = new HashMap<>();
        this.playerToTheirSharedInventory = new HashMap<>();

        this.lastDM = new HashMap<>();
        this.seenJoinOf = new HashMap<>();
    }

    @SuppressWarnings("unused") // api
    public RegistryAccess registryAccess() {
        return chatRegistries;
    }

    public void sendHello(Player from, Player joined) {
        uxModule.storage().getUserDataContainer(from).thenAccept(container -> {
            if(container == null) return;
            Task.ensureAsync(() -> sendHelloInternal(from, container, joined));
        });
    }

    private void sendHelloInternal(Player from, DataContainer fromContainer, Player joined) {
        if(from == joined) return;

        Set<UUID> seenJoin = seenJoinOf.get(fromContainer.identifier().gameUuid());
        if(seenJoin == null || !seenJoin.remove(joined.getUniqueId())) return;

        AsyncJoinReactionEvent event = new AsyncJoinReactionEvent(
                fromContainer,
                joined,
                UXConfiguration.I.chat.joinQuit.hello
        );
        if(!event.callEvent()) return;

        sendChatMessage(
                from,
                fromContainer,
                Placeholders.resolveDynamic(
                        "!" + event.helloFormat(),
                        joined,
                        PLAYER_NAME_PLACEHOLDER
                )
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

    // note: adventure is a good lib until it comes to handling player input
    private static String extractRawInput(Component message) {
        try {
            Class<?> textComponentImpl_class = Class.forName("net.kyori.adventure.text.TextComponentImpl");
            if(!textComponentImpl_class.isInstance(message)) throw new RuntimeException();

            Field content_field = textComponentImpl_class.getDeclaredField("content");
            content_field.setAccessible(true);
            return (String) content_field.get(message);
        }
        catch (Exception _) {
        }

        // fallback
        return Text.serialize(message);
    }

    public void sendChatMessage(Player sender, DataContainer senderContainer, Component message) {
        sendChatMessage(sender, senderContainer, extractRawInput(message));
    }

    public void sendChatMessage(Player sender, DataContainer senderContainer, String content) {
        if(content.isEmpty()) return;

        long creationTime = System.currentTimeMillis();

        StringBuilder contentBuilder = new StringBuilder(Text.restrictTags(content)); // save input for event

        char firstCharacter = content.charAt(0);
        ChatChannel chatChannel = null;
        for (ChatChannel registeredChannel : chatRegistries.chatChannelRegistry.elements()) {
            char channelPrefix = registeredChannel.prefixSymbol();
            if(channelPrefix != '\0' && channelPrefix == firstCharacter) {
                chatChannel = registeredChannel;
                break;
            }
        }

        if(chatChannel == null)
            chatChannel = chatRegistries.localChatChannel;
        else {
            do {
                contentBuilder.deleteCharAt(0);
            }
            while (contentBuilder.charAt(0) == ' ');

            if(contentBuilder.isEmpty()) return;
        }

        int length = contentBuilder.length();
        while (contentBuilder.charAt(length - 1) == '\\') {
            contentBuilder.deleteCharAt(--length);
        }

        // URL Processing
        Matcher urlMatcher = BOXED_URL_PATTERN.matcher(contentBuilder);
        if(urlMatcher.find() && validateURL(urlMatcher.group("url"))) {
            String url = urlMatcher.group("url");
            contentBuilder.delete(0, contentBuilder.length());
            contentBuilder.insert(0, String.format(
                    BOXED_URL_FORMAT,
                    urlMatcher.group("prefix"),
                    url, url,
                    urlMatcher.group("text"),
                    urlMatcher.group("suffix")
            ));
        }
        else if((urlMatcher = URL_PATTERN.matcher(contentBuilder)).find() && validateURL(urlMatcher.group("url"))) {
            String url = urlMatcher.group("url");
            contentBuilder.delete(0, contentBuilder.length());
            contentBuilder.insert(0, String.format(
                    URL_FORMAT,
                    urlMatcher.group("prefix"),
                    url, url, url,
                    urlMatcher.group("suffix")
            ));
        }

        var chatConfig = UXConfiguration.I.chat;

        LinkedHashSet<FormatCode> codeBuffer = new LinkedHashSet<>();
        if(chatConfig.colorFormatting && DefaultPermission.CHAT_FORMATTING.hasPermission(sender)) {
            int index;
            int indexThreshold = -1;
            while ((index = contentBuilder.indexOf("&")) != -1 && index > indexThreshold) {
                if(index >= contentBuilder.length() - 1) break;

                indexThreshold = index;

                FormatCode code = FormatCode.INDEX.get(contentBuilder.charAt(index + 1));
                if(code == null) continue;

                if(code == FormatCode.RESET) {
                    contentBuilder.delete(index, index + 2);

                    int offsetIndex = index;
                    while (!codeBuffer.isEmpty()) {
                        String toInsert = "</" + codeBuffer.removeLast().minimessage + '>';
                        contentBuilder.insert(offsetIndex, toInsert);
                        offsetIndex += toInsert.length();
                    }
                    continue;
                }

                if(codeBuffer.contains(code)) contentBuilder.replace(index, index + 2, "");
                else {
                    codeBuffer.addLast(code);
                    contentBuilder.replace(index, index + 2, '<' + code.minimessage + '>');
                }
            }
        }

        if(chatConfig.replacements.enabled)
            Replacement.fillWithReplacements(chatRegistries.replacementRegistry, sender, contentBuilder);

        AsyncPreChatMessageEvent event = new AsyncPreChatMessageEvent(
                sender,
                senderContainer,
                content,
                creationTime,
                chatChannel,
                contentBuilder.toString()
        );

        if(!event.callEvent()) return;

        sendPreProcessed(new PreProcessedMessage(
                sender,
                senderContainer,
                content,
                creationTime,
                chatChannel,
                event.preProcessedContent
        ));
    }

    private boolean findAndReplaceMention(Player player, StringBuilder content, @Nullable Set<Player> mentioned) {
        final String substringToFind = UXConfiguration.I.chat.mentions.requireAtSymbol ? ("@" + player.getName()) : player.getName();
        final int substringLength = substringToFind.length();

        int index;
        int indexThreshold = 0;
        String formattedMention = null; // do not create instance until it's necessary
        int formattedMentionLength = -1;
        while ((index = content.indexOf(substringToFind, indexThreshold)) != -1) {
            if(index >= content.length() -1) break;

            if(formattedMention == null) {
                formattedMention = Placeholders.resolveDynamic(UXConfiguration.I.chat.mentions.formatting, player, PLAYER_NAME_PLACEHOLDER);
                formattedMentionLength = formattedMention.length();
            }

            indexThreshold = index + formattedMentionLength;

            content.replace(index, index + substringLength, formattedMention);
        }

        if(indexThreshold != 0 && mentioned != null) mentioned.add(player);

        return indexThreshold != 0;
    }

    public void sendPreProcessed(final PreProcessedMessage message) {
        var chatConfig = UXConfiguration.I.chat;

        Player sender = message.sender();
        StringBuilder content = new StringBuilder(message.preProcessedContent());

        Set<Player> mentioned = new HashSet<>();
        var mentionsConfig = chatConfig.mentions;

        if(mentionsConfig.enabled && mentionsConfig.showToEveryone) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if(sender != null && player == sender) continue;

                findAndReplaceMention(player, content, mentioned);
            }
        }

        // receivers step 1: requesting 'em from the ChatChannel
        var receiversOrError = message.chatChannel().resolveReceivers(sender, message.senderContainer());
        if(receiversOrError.secondPresent()) {
            if(sender != null) sender.sendMessage(Messages.ERROR.create(receiversOrError.second()));
            return;
        }

        // receivers step 2: copy result
        Set<Player> receivers = receiversOrError.first();

        // receivers step 3: remove everyone who ignore player in global chat
        if(chatConfig.ignoring.enabled) {
            receivers.removeIf(player -> {
                if(player == sender) return false;

                try {
                    Identifier receiverIdentifier = uxModule.storage().getIdentifier(Either.ofSecond(player.getName())).get();
                    return uxModule.chat().isIgnoring(receiverIdentifier, message.senderContainer().identifier(), IgnoringType.CHAT);
                }
                catch (Throwable ex) {
                    return false;
                }
            });
        }

        // receivers step 4: allow third-party listeners to modify receivers list
        new AsyncChatChooseReceiversEvent(message.senderContainer(), message.chatChannel(), receivers).callEvent();

        // we should create at least one receiver-lost result variant for console and spying
        String sharedRawResult = Placeholders.resolve(
                message.chatChannel().format(),
                Placeholder.create("player_name", message.senderContainer().identifier()::gameName),
                Placeholder.createFinal("message", content.toString())
        );
        Component sharedResult = Text.create(sharedRawResult);

        // send to console and to spying processing
        Bukkit.getConsoleSender().sendMessage(sharedResult);

        if(message.chatChannel() == chatRegistries.localChatChannel) {
            assert sender != null;

            var localChatConfig = chatConfig.localChat;
            if (receivers.size() == 1 && localChatConfig.nobodyHeardEnabled)
                sender.sendMessage(Text.create(localChatConfig.nobodyHeard));

            sendForSpying(message.sender().getName(), receivers, message.originalContent(), content.toString(), sharedRawResult);
        }

        // send result message

        Notification mentionNotification = null;
        for (Player player : receivers) {
            if(!mentionsConfig.enabled || mentionsConfig.showToEveryone) {
                if(mentioned.contains(player)) {
                    if(mentionNotification == null) {
                        var notification = chatConfig.mentions.notification;
                        mentionNotification = new Notification(
                                NotificationType.MENTION,
                                Placeholders.resolve(
                                        notification.text.convert().determine(message.senderContainer()),
                                        Placeholder.create("mentioner_name", message.senderContainer().identifier()::gameName)
                                ),
                                notification.sound.convert()
                        );
                    }

                    uxModule.notifications().sendNotification(
                            player,
                            mentionNotification
                    );
                }

                player.sendMessage(sharedResult);
                continue;
            }

            StringBuilder contentCopy = new StringBuilder(content);
            if(findAndReplaceMention(player, contentCopy, null)) {
                if(mentionNotification == null) {
                    var notification = chatConfig.mentions.notification;
                    mentionNotification = new Notification(
                            NotificationType.MENTION,
                            Placeholders.resolve(
                                    notification.text.convert().determine(message.senderContainer()),
                                    Placeholder.create("mentioner_name", message.senderContainer().identifier()::gameName)
                            ),
                            notification.sound.convert()
                    );
                }

                uxModule.notifications().sendNotification(
                        player,
                        mentionNotification
                );
            }

            player.sendMessage(Text.create(Placeholders.resolve(
                    message.chatChannel().format(),
                    Placeholder.create("player_name", message.senderContainer().identifier()::gameName),
                    Placeholder.createFinal("message", content.toString())
            )));
        }
    }

    private void sendForSpying(String senderName, Set<Player> receivers, String input, String formattedInput, String formattedMessage) {
        Component result = Text.create(Placeholders.resolve(
                UXConfiguration.I.chat.localChat.spying.format,
                Placeholder.createFinal("sender_name", senderName),
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
        Storage<DataContainer> storage = uxModule.storage();
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
        var chatConfig = UXConfiguration.I.chat;
        var directMessagesConfig = chatConfig.directMessages;

        if(sender == receiver) {
            sender.sendMessage(Messages.ERROR.create(directMessagesConfig.cantSendYourself));
            return;
        }

        if (chatConfig.ignoring.enabled) {
            if (isIgnoring(senderContainer.identifier(), receiverContainer.identifier(), IgnoringType.DIRECT_MESSAGES)) {
                sender.sendActionBar(Text.create(Placeholders.resolve(
                        directMessagesConfig.haveBlocked.convert().determine(receiverContainer),
                        Placeholder.create("receiver_name", receiver::getName)
                )));
                return;
            }

            if (isIgnoring(receiverContainer.identifier(), senderContainer.identifier(), IgnoringType.DIRECT_MESSAGES)) {
                sender.sendActionBar(Text.create(Placeholders.resolve(
                        directMessagesConfig.areBlocked.convert().determine(receiverContainer),
                        Placeholder.create("receiver_name", receiver::getName)
                )));
                return;
            }
        }

        // todo: message processing logic from main chat
        StringBuilder builder = new StringBuilder(message);
        int length = builder.length();
        while (builder.charAt(length - 1) == '\\') {
            builder.deleteCharAt(--length);
        }

        if(builder.isEmpty()) return;

        final String finalMessage = builder.toString();

        uxModule.executorService().execute(() -> {
            Placeholder[] placeholders = new Placeholder[]{
                    Placeholder.create("receiver_name", receiver::getName),
                    Placeholder.create("sender_name", sender::getName),
                    Placeholder.createFinal("message", finalMessage)
            };
            AsyncDirectMessageEvent event = new AsyncDirectMessageEvent(
                    sender,
                    receiver,
                    finalMessage,
                    Text.create(Placeholders.resolve(
                            directMessagesConfig.receiverFormat,
                            placeholders
                    )),
                    Text.create(Placeholders.resolve(
                            directMessagesConfig.senderFormat,
                            placeholders
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
                identifiers.add(uxModule.storage().getIdentifier(Either.ofFirst(uuid)).get());
            }
            catch (Throwable _) {
            }
        }
        return identifiers;
    }

    // ignore
    public Set<UUID> getIgnoringSet(Identifier identifier, IgnoringType ignoringType) {
        if(!UXConfiguration.I.chat.ignoring.enabled) return Set.of();

        return Set.copyOf(getIgnoringSet(identifier, ignoringType.field));
    }

    public boolean isIgnoring(Identifier identifier, Identifier toCheck, IgnoringType ignoringType) {
        if(!UXConfiguration.I.chat.ignoring.enabled) return false;
        return getIgnoringSet(identifier, ignoringType).contains(toCheck.uuid());
    }

    public void setIgnoring(Identifier identifier, Identifier toIgnore, IgnoringType ignoringType, boolean ignoring) {
        if(!UXConfiguration.I.chat.ignoring.enabled) return;

        Set<UUID> set = getIgnoringSet(identifier, ignoringType.field);

        if(set.contains(toIgnore.uuid()) == ignoring) return;

        if(ignoring) set.add(toIgnore.uuid());
        else set.remove(toIgnore.uuid());

        setSet(identifier, ignoringType.field, set);
    }

    private Set<UUID> getIgnoringSet(Identifier identifier, DataField<Set<UUID>> field) {
        if(!UXConfiguration.I.chat.ignoring.enabled) return Set.of();

        return getDataContainer(identifier)
                .map(container -> container.get(field))
                .orElse(new HashSet<>());
    }

    private void setSet(Identifier identifier, DataField<Set<UUID>> field, Set<UUID> value) {
        if(!UXConfiguration.I.chat.ignoring.enabled) return;

        getDataContainer(identifier).ifPresent(container ->
                container.set(field, value)
        );
    }

    private Optional<DataContainer> getDataContainer(Identifier identifier) {
        try {
            return Optional.ofNullable(uxModule.storage().getUserDataContainer(identifier, true, false).get());
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
