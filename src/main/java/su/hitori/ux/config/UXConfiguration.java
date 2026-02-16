package su.hitori.ux.config;

import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import su.hitori.api.config.Configuration;
import su.hitori.ux.notification.NotificationType;

import java.nio.file.Path;
import java.util.List;

public final class UXConfiguration extends Configuration {

    public static UXConfiguration I;

    public UXConfiguration(Path path) {
        super(path);
        I = this;
    }

    public Advertisements advertisements = new Advertisements();
    public NameTags nameTags = new NameTags();
    public Tab tab = new Tab();
    public Chat chat = new Chat();
    public Events events = new Events();
    public Streams streams = new Streams();
    public Storage storage = new Storage();

    public static final class Advertisements {
        public boolean enabled = true;
        @Comment(value = @CommentValue(" In seconds"))
        public int period = 1800;
        @Comment(value = @CommentValue(" Stay time. In seconds"))
        public int stay = 5;
        public List<String> list = List.of();
    }

    public static final class NameTags {
        public boolean enabled = true;
        public String format = "%player_name%";
        public boolean background = true;
    }

    public static final class Tab {
        @Comment(value = {@CommentValue(" Set to false to disable tab processing")})
        public boolean enabled = true;
        public int updateIntervalSeconds = 1;
        public List<String> header = List.of("", "example tab", "");
        public List<String> footer = List.of("", "tps %tps% | mspt %mspt% | online %online% | ping %ping%", "");
        public String playerName = "%sort_order% %player_name%";

        @Comment(value = {@CommentValue(" Yellow numbers to display right after the player name. Leave empty to display nothing. Placeholders are player_name, ping")})
        public String objective = "<aqua>%ping%ms</aqua>";
    }

    public static final class Chat {
        @Comment(value = {@CommentValue(" Set to false to disable chat processing")})
        public boolean enabled = true;

        @Comment(value = {@CommentValue(" Module will try to replace username with formatted one in vanilla messages like advancements")})
        public ReplaceVanillaUsername replaceVanillaUsername = new ReplaceVanillaUsername();

        public String noSuchPlayer = "There's no player with name <yellow>%player_name%</yellow>!";

        public Mentions mentions = new Mentions();
        public Structure structure = new Structure();
        public DirectMessages directMessages = new DirectMessages();
        public LocalChat localChat = new LocalChat();
        public JoinQuit joinQuit = new JoinQuit();
        public Ignoring ignoring = new Ignoring();
        public Replacements replacements = new Replacements();
        public Gender gender = new Gender();
        public FirstVisit firstVisit = new FirstVisit();
        public String sharedInventoryFormat = "<hover:show_text:'Click to view %player_name% inventory'><click:run_command:'/sharedinventory %shared_inventory_uuid%'>[%player_name% inventory]";

        public static final class FirstVisit {
            public boolean enabled = true;
            public int secondsToSpend = 180;
            public GenderInfluencedText message = new GenderInfluencedText(
                    """
                            \s
                              Hey, %player_name%! It seems you've played first 3 minutes on our server, we hope you're enjoying it :D
                              We've also prepared some pages for newbies:
                              - [example.com]
                             \s"""
            );
            public Sound sound = new Sound("block.amethyst_block.hit");
        }

        public static final class Mentions {
            public boolean enabled = true;

            @Comment(value = {@CommentValue(" Formatting for mention. <player_name> returns mentioned player name.")})
            public String formatting = "<bold>%player_name%</bold>";

            @Comment(value = {@CommentValue(" Notification format. %mentioner_name% for name of who mentioned player and %player_name% for mentioned name. Gender for message will be taken from mentioner.")})
            public Notification notification = new Notification(
                    NotificationType.MENTION,
                    new GenderInfluencedText("%mentioner_name% mentioned you."),
                    new Sound("block.amethyst_block.hit")
            );
        }

        public static final class Structure {
            public String local = "<gray>| <white>%player_name%</white> ›</gray> <white><click:suggest_command:'/msg %player_name% '>%message%</white>";
            public String global = "<#50f2b1>| <white>%player_name%</white> »</#50f2b1> <white><click:suggest_command:'/msg %player_name% '>%message%</white>";
        }

        public static final class DirectMessages {
            public String receiverFormat = "<color:#479dff>[%sender_name% » I]:</color> <white><click:suggest_command:'/tell %sender_name% '>%message%</white>";
            public String senderFormat = "<color:#47ff8e>[I » %receiver_name%]:</color> <white><click:suggest_command:'/tell %receiver_name% '>%message%</white>";
            public Sound receiveSound = new Sound("block.amethyst_block.hit");

            public String cantSendYourself = "You can't send message to yourself!";
            public String noRecentMessage = "We doesn't know anything about your recent messages, so use <yellow>/tell</yellow> instead!";

            public GenderInfluencedText haveBlocked = new GenderInfluencedText(
                    "You can't send message to <yellow>%receiver_name%</yellow> because you've blocked him!",
                    "You can't send message to <yellow>%receiver_name%</yellow> because you've blocked her!"
            );
            public GenderInfluencedText areBlocked = new GenderInfluencedText(
                    "You can't send message to <yellow>%receiver_name%</yellow> because he's blocked you!",
                    "You can't send message to <yellow>%receiver_name%</yellow> because she's blocked you!"
            );
        }

        public static final class LocalChat {
            @Comment({@CommentValue(" Set value to -1 to make local chat on server")})
            public int radius = 100;

            public Spying spying = new Spying();

            public boolean nobodyHeardEnabled = true;
            public String nobodyHeard = "Nobody heard you.";

            public static final class Spying {
                @Comment({@CommentValue(" %input% for original message, %formatted_input% for formatted input (not the full message), %formatted_message% for full message sent to players and %sender_name% for message sender name")})
                public String format = "<color:gray>spying</color> %formatted_message%";
                public String enabledSpying = "You've <u><green>enabled</u> spying mode.";
                public String disableSpying = "You've <u><red>disabled</u> spying mode.";
            }
        }

        public static final class JoinQuit {
            @Comment(value = @CommentValue(" Insert a click event here with command /hello %player_name% to let already online players send hello message for joined"))
            public GenderInfluencedText join = new GenderInfluencedText("<click:run_command:'/hello %player_name%'>%player_name% <color:#47ff69>joined the server</color></click>");
            public GenderInfluencedText quit = new GenderInfluencedText("%player_name% <color:#ff4760>left the server</color>");

            @Comment(value = @CommentValue(" Sends a hello message by the player who sent /hello <player>"))
            public String hello = "Hey, %player_name% :)";
        }

        public static final class Ignoring {
            public String ignoredNow = "You are now ignoring <yellow>%ignored_name%</yellow> in %ignoring_type%.";
            public String alreadyIgnored = "You are already ignoring <yellow>%ignored_name%</yellow> in %ignoring_type%!";
            public String unignoredNow = "You are no longer ignoring <yellow>%ignored_name%</yellow> in %ignoring_type%.";
            public String notIgnored = "You are not ignoring <yellow>%ignored_name%</yellow> in %ignoring_type%!";
            public String cantIgnoreYourself = "You can't ignore yourself!";

            public IgnoringList list = new IgnoringList();

            public String chat = "<aqua>chat</aqua>";
            public String directMessages = "<aqua>direct messages</aqua>";

            @Comment(value = @CommentValue(" Place a username here to make it impossible to ignore this player."))
            public List<String> ignoringResistant = List.of();
            public GenderInfluencedText tryToIgnoreResistant = new GenderInfluencedText(
                    "You can't start ignoring this player, because he is ignoring-resistant!",
                    "You can't start ignoring this player, because she is ignoring-resistant!"
            );

            public static final class IgnoringList { // name in favour of java.util.List
                public String notIgnoreAnyone = "You don't ignore anyone!";

                @Comment(@CommentValue(" %n% for line break (if there is at least one entry, otherwise empty)"))
                public String listFormat = "Ignoring list:%n%%entries%";

                @Comment(@CommentValue(" %n% for line break (if there is next entry, otherwise empty) or %c% for comma and space: \", \" (also if there's next entry, otherwise empty)"))
                public String entryFormat = " - %ignored_name% <dark_gray>(%ignoring_type%)</dark_gray>%n%";

                public String both = "both";
                public String chat = "chat";
                public String directMessages = "dm";
            }
        }

        public static final class Replacements {
            public String allFormat = "<gray>%formatted_replacement%</gray>";

            public String ping = "%ping% ms";
            public String location = "Coordinates: %x%; %y%; %z%";
            public String playtime = "Playtime: %playtime%";
            public String mobkills = "Mobs kills: %mobkills%";
            public String mineblocks = "Blocks mined: %mineblocks%";
            public String deaths = "Deaths: %deaths%";
            public String lastdeath = "Time since last death: %lastdeath%";
            public String showitem = "%item%";
            public String showinv = "%open_inventory%";
            public String dice = "Dice: %dice%";
            public String xp = "Experience: %xp_level% lvl.";
        }

        public static final class ReplaceVanillaUsername {
            public boolean enabled = true;
            public String usernameFormat = "%player_name%";
        }

        public static final class Gender {
            public String now_man = "You've set the gender to <aqua>male</aqua>.";
            public String already_man = "You've already set the gender to <aqua>male</aqua>!";
            public String now_woman = "You've set the gender to <aqua>female</aqua>.";
            public String already_woman = "You've already set the gender to <aqua>female</aqua>!";
        }
    }

    public static final class Events {
        @Comment(value = {@CommentValue(" Time zone for events. https://en.wikipedia.org/wiki/List_of_tz_database_time_zones (take TZ identifier from table)")})
        public String timeZone = "Europe/Moscow";

        public String alreadyHidden = "You've already hidden information about event!";
        public String hidden = "Information about <bold>this</bold> event will no longer appear.";
        public String noEvent = "There's no such event planned!";
        public String alreadyPlanned = "There's already 3 events planned! End the old one using <yellow><click:run_command:'/event end'><hover:show_text:'Click to run this command'>[\"/event end\"]</yellow> to plan the new one!";

        public String reminder = """
                    \s
                      Event planned: <color:#69a2ff>"%event_name%"</color>
                      %event_description%
                      Start time: <color:#69a2ff>%event_start_time%</color>
                      <gray>To hide next reminders, click here <click:run_command:'/event ok %event_uuid%'><hover:show_text:'Click to run event info'>[Hide]</gray>
                     \s"""
                .replace("\n", "<br>");
        public String eventHappening = """
                    \s
                      Event happening: <color:#69a2ff>"%event_name%"</color>
                      %event_description%
                      <gray>To hide next reminders, click here: <click:run_command:'/event ok %event_uuid%'><hover:show_text:'Click to run hide event info'>[Hide]</gray>
                     \s"""
                .replace("\n", "<br>");
        public Sound reminderSound = new Sound("block.amethyst_block.hit");
        public String ended = "<br>  Event <color:#69a2ff>\"%event_name%\"</color> has ended!<br>  We'll be waiting for you again :)<br>";

        public EventsList list = new EventsList();

        public static final class EventsList {
            public String listFormat = "There's few active events:<br>%entries%";

            @Comment(@CommentValue(" %n% for line break (if there is next entry, otherwise empty) or %c% for comma and space: \", \" (also if there's next entry, otherwise empty)"))
            public String entryFormat = " - <click:run_command:/event view %event_uuid%><hover:show_text:'Click to view event description'><color:#69a2ff>[%event_name%]</click>%n%";
        }
    }

    public static final class Streams {
        public String start = """
                \s
                  %player_name% started the stream!
                  <color:#69a2ff><hover:show_text:'Click to open browser'><click:open_url:'%url_full%'>[Watch stream on %url_domain%]</color>
                 \s""";
        public String end = """
                \s
                  %player_name% ended the stream!
                 \s""";

        public String noStream = "You didn't start stream";
        public String noStreamAdmin = "%player_name% didn't start the stream!";
        public String alreadyStarted = "You've already started the stream!";

        public String youHaveStarted = "You have started the stream!";
        public String youHaveEnded = "You have ended the stream!";
        public String youHaveEndedAdmin = "You have ended the stream of %player_name%";

        public OngoingStreams ongoingStreams = new OngoingStreams();

        public String nonAllowedDomain = "Domain <yellow>\"%url_domain%\"</yellow> is not allowed. Allowed are: %allowed_domains%";
        public List<String> allowedDomains = List.of(
                "twitch.tv",
                "youtube.com",
                "youtu.be"
        );

        public Sound startSound = new Sound("block.amethyst_block.hit");

        public static class OngoingStreams {
            public String listFormat = "Ongoing streams:\n%entries%";

            @Comment(@CommentValue(" %n% for line break (if there is next entry, otherwise empty) or %c% for comma and space: \", \" (also if there's next entry, otherwise empty)"))
            public String entryFormat = " - %streamer_name%: <color:#69a2ff><hover:show_text:'Click to open browser'><click:open_url:'%url_full%'>[Watch stream on %url_domain%]</color>%n%";
        }
    }

    public static final class Storage {
        @Comment(value = {@CommentValue(" default or thirdparty. leave thirdparty if you want other module to replace storage logic")})
        public String implementation = "default";

        public DefaultImplementation defaultImplementation = new DefaultImplementation();

        public static final class DefaultImplementation {
            @Comment(value = {@CommentValue(" mysql, h2")})
            public String type = "h2";
            public String host = "localhost:3306";
            public String database = "database";
            public String user = "root";
            public String password = "root";
        }
    }

    public static final class GenderInfluencedText implements ConvertableObject<su.hitori.ux.GenderInfluencedText> {
        public String male;
        public String female;

        public GenderInfluencedText(String same) {
            this(same, same);
        }

        public GenderInfluencedText(String male, String female) {
            this.male = male;
            this.female = female;
        }

        @Override
        public su.hitori.ux.GenderInfluencedText convert() {
            return new su.hitori.ux.GenderInfluencedText(male, female);
        }
    }

    public static final class Notification implements ConvertableObject<su.hitori.ux.notification.Notification> {
        private final transient NotificationType type;
        public GenderInfluencedText text;
        public Sound sound;

        public Notification(NotificationType type, GenderInfluencedText text, Sound sound) {
            this.type = type;
            this.text = text;
            this.sound = sound;
        }

        /**
         * After convert GenderInfluencedText will be converted to just male variant.
         */
        @Override
        public su.hitori.ux.notification.Notification convert() {
            return new su.hitori.ux.notification.Notification(type, text.male, sound.convert());
        }
    }

    public static final class Sound implements ConvertableObject<su.hitori.ux.Sound> {
        public String name;
        public double volume;
        public double pitch;

        public Sound() {
            name = "";
            volume = pitch = 1;
        }

        public Sound(String name) {
            this(name, 1, 1);
        }

        public Sound(String name, double volume, double pitch) {
            this.name = name;
            this.volume = volume;
            this.pitch = pitch;
        }

        public float volume() {
            return (float) volume;
        }

        public float pitch() {
            return (float) pitch;
        }

        @Override
        public su.hitori.ux.Sound convert() {
            return new su.hitori.ux.Sound(name, volume(), pitch());
        }
    }

}
