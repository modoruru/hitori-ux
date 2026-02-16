package su.hitori.ux.chat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import su.hitori.api.Hitori;
import su.hitori.api.module.ModuleDescriptor;
import su.hitori.api.registry.MappedRegistry;
import su.hitori.api.registry.Registry;
import su.hitori.api.registry.RegistryAccess;
import su.hitori.api.registry.RegistryKey;
import su.hitori.api.util.Text;
import su.hitori.api.util.UnsafeUtil;
import su.hitori.ux.UXModule;
import su.hitori.ux.chat.channel.ChatChannel;
import su.hitori.ux.chat.replacement.Replacement;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.placeholder.DynamicPlaceholder;
import su.hitori.ux.placeholder.Placeholder;
import su.hitori.ux.placeholder.Placeholders;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatRegistries implements RegistryAccess {

    public static final RegistryKey<ChatChannel> CHAT_CHANNEL_REGISTRY = new RegistryKey<>(Key.key("hitori", "chat_channel"), ChatChannel.class);
    public static final RegistryKey<Replacement> REPLACEMENT_REGISTRY = new RegistryKey<>(Key.key("hitori", "replacement"), Replacement.class);

    final Registry<ChatChannel> chatChannelRegistry;
    final Registry<Replacement> replacementRegistry;

    ChatChannel localChatChannel;

    ChatRegistries() {
        this.chatChannelRegistry = new MappedRegistry<>(CHAT_CHANNEL_REGISTRY);
        this.replacementRegistry = new MappedRegistry<>(REPLACEMENT_REGISTRY);

        bootstrapChatChannels();
        bootstrapReplacements();
    }

    private void bootstrapChatChannels() {
        localChatChannel = ChatChannel.createLocal();
        ChatChannel globalChatChannel = ChatChannel.createGlobal();
        chatChannelRegistry.register(localChatChannel.key(), localChatChannel);
        chatChannelRegistry.register(globalChatChannel.key(), globalChatChannel);
    }

    private void createReplacement(String key, String word, String format, Set<DynamicPlaceholder<Player>> placeholders) {
        Replacement replacement = Replacement.createDefault(Key.key("hitori", key), word, format, placeholders);
        replacementRegistry.register(replacement.key(), replacement);
    }

    private void bootstrapReplacements() {
        var cfg = UXConfiguration.I.chat.replacements;
        createReplacement("ping", "ping", cfg.ping, Set.of(DynamicPlaceholder.create("ping", Player::getPing)));
        createReplacement("location", "location", cfg.location, Set.of(
                DynamicPlaceholder.create("x", player -> player.getLocation().getBlockX()),
                DynamicPlaceholder.create("y", player -> player.getLocation().getBlockY()),
                DynamicPlaceholder.create("z", player -> player.getLocation().getBlockZ())
        ));
        createReplacement("play_time", "playtime", cfg.playtime, Set.of(
                DynamicPlaceholder.create(
                        "playtime", player -> String.format(
                                "%.2fh",
                                (player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60d)) // thanks bukkit for naming statistic with minute and counting in ticks
                        )
                )
        ));
        createReplacement("mobs_kills", "mobkills", cfg.mobkills, Set.of(DynamicPlaceholder.create(
                "mobkills", player -> player.getStatistic(Statistic.MOB_KILLS)
        )));
        createReplacement("mined_blocks", "mineblocks", cfg.mineblocks, Set.of(DynamicPlaceholder.create(
                "mineblocks", player -> {
                    AtomicLong allCount = new AtomicLong();

                    org.bukkit.Registry.BLOCK.keyStream()
                            .map(org.bukkit.Registry.MATERIAL::get)
                            .filter(Objects::nonNull)
                            .forEach(material -> {
                                allCount.set(allCount.get() + player.getStatistic(Statistic.MINE_BLOCK, material));
                            });

                    return allCount.get();
                }
        )));
        createReplacement("deaths", "deaths", cfg.deaths, Set.of(DynamicPlaceholder.create(
                "deaths",
                player -> player.getStatistic(Statistic.DEATHS)
        )));
        createReplacement("last_death", "lastdeath", cfg.lastdeath, Set.of(DynamicPlaceholder.create(
                "lastdeath",
                player -> TimeUtil.formatLength(player.getStatistic(Statistic.TIME_SINCE_DEATH) * 50L)
        )));
        createReplacement("show_item", "showitem", cfg.showitem, Set.of(DynamicPlaceholder.create(
                "item",
                player -> Text.serialize(player.getInventory().getItemInMainHand().displayName())
        )));
        createReplacement("show_inventory", "showinv", cfg.showinv, Set.of(
                DynamicPlaceholder.create(
                        "open_inventory",
                        player -> {
                            Chat chat = Hitori.instance().moduleRepository().getModule(Key.key("hitori", "ux"))
                                    .map(ModuleDescriptor::getInstance)
                                    .map(module -> (UXModule) module)
                                    .map(UXModule::chat)
                                    .orElseThrow();

                            SharedInventoryContainer sharedInventory = chat.createSharedInventory(player);

                            return Placeholders.resolve(
                                    UXConfiguration.I.chat.sharedInventoryFormat,
                                    Placeholder.create("player_name", player::getName),
                                    Placeholder.create("shared_inventory_uuid", sharedInventory::uuid)
                            );
                        }
                )
        ));
        createReplacement("dice", "dice", cfg.dice, Set.of(
                DynamicPlaceholder.create(
                        "dice",
                        _ -> "⚀⚁⚂⚃⚄⚅".charAt((int) (Math.random() * 5))
                )
        ));
        createReplacement("experience", "xp", cfg.xp, Set.of(DynamicPlaceholder.create(
                "xp_level",
                Player::getLevel
        )));
    }

    @Override
    public <E extends Keyed> Optional<Registry<E>> access(RegistryKey<E> key) throws IllegalAccessError {
        Registry<?> registry;
        if(key == CHAT_CHANNEL_REGISTRY) registry = chatChannelRegistry;
        else if(key == REPLACEMENT_REGISTRY) registry = replacementRegistry;
        else registry = null;

        return Optional.ofNullable(registry)
                .map(UnsafeUtil::cast);
    }

}
