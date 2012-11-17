package de.raidcraft.guestunlock;

import com.sk89q.commandbook.CommandBook;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.zachsthings.libcomponents.ComponentInformation;
import com.zachsthings.libcomponents.Depend;
import com.zachsthings.libcomponents.bukkit.BukkitComponent;
import com.zachsthings.libcomponents.config.ConfigurationBase;
import com.zachsthings.libcomponents.config.Setting;
import de.raidcraft.api.database.Database;
import de.raidcraft.api.database.Table;
import de.raidcraft.util.EnumUtils;
import de.raidcraft.util.PaginatedResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Silthus
 */
@ComponentInformation(
        friendlyName = "Guest Component",
        desc = "Unlocks Guests when their application has been accepted."
)
@Depend(plugins = {"RaidCraft-API"})
public class GuestComponent extends BukkitComponent implements Listener {

    private static final Random random = new Random();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private LocalConfiguration config;

    @Override
    public void enable() {

        this.config = configure(new LocalConfiguration());

        registerCommands(Commands.class);
        CommandBook.registerEvents(this);

        if (Database.getInstance() == null) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(CommandBook.inst(), new Runnable() {
                @Override
                public void run() {
                    Database.getInstance().registerTable(GuestTable.class, new GuestTable());
                }
            }, 1L);
        } else {
            Database.getInstance().registerTable(GuestTable.class, new GuestTable());
        }
        // start a task that notifies players when their application was accepted
        Bukkit.getScheduler().scheduleSyncRepeatingTask(CommandBook.inst(), new Runnable() {
            @Override
            public void run() {

                GuestTable table = Database.getTable(GuestTable.class);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    List<PlayerData> players = table.getPlayers(player.getName());
                    for (PlayerData data : players) {
                        if (data.isAcceptedAndLocked()) {
                            data.unlock();
                        }
                    }
                }
            }
        }, config.task_delay * 20, config.task_delay * 20);
    }

    private String generateCaptcha() {

        StringBuffer captchaStringBuffer = new StringBuffer();
        for (int i = 0; i < config.captcha_length; i++) {
            int baseCharNumber = Math.abs(random.nextInt()) % 62;
            int charNumber;
            if (baseCharNumber < 26) {
                charNumber = 65 + baseCharNumber;
            } else if (baseCharNumber < 52){
                charNumber = 97 + (baseCharNumber - 26);
            } else {
                charNumber = 48 + (baseCharNumber - 52);
            }
            captchaStringBuffer.append((char)charNumber);
        }

        return captchaStringBuffer.toString();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {

        // lets generate that player in the database
        // the database will return if player already exists
        Database.getTable(GuestTable.class).addPlayer(event.getPlayer().getName());
        Database.getTable(GuestTable.class).getPlayer(event.getPlayer().getName()).updateLastJoin();
    }

    public static class LocalConfiguration extends ConfigurationBase {

        @Setting("task-delay")public int task_delay = 60;
        @Setting("captcha-length")public int captcha_length = 6;
    }

    public class Commands {

        @Command(
                aliases = {"gast", "unlock", "gunlock", "gu", "guest"},
                desc = "Schaltet Spieler nach erfolgreicher Bewerbung frei.",
                usage = "<player>",
                min = 1
        )
        @CommandPermissions("guestunlock.unlock")
        public void unlock(CommandContext args, CommandSender sender) throws CommandException {

            PlayerData player = Database.getTable(GuestTable.class).getPlayer(args.getString(0));
            if (player == null) {
                throw new CommandException("Es gibt keinen Spieler mit dem Namen: " + args.getString(0));
            }
            if (player.unlocked != null) {
                throw new CommandException("Der Spieler wurde bereits freigeschaltet.");
            }
            // lets check the captcha CaSE_SEnsteTive
            player.unlock();
        }

        @Command(
                aliases = {"guestlist", "gl"},
                desc = "Lists guests and their unlock and join dates.",
                usage = "<partial player name>",
                min = 1,
                flags = "p:"
        )
        @CommandPermissions("guestunlock.list")
        public void list(CommandContext args, CommandSender sender) throws CommandException {

            new PaginatedResult<PlayerData>("Player  -  First Join  -  Unlocked") {

                @Override
                public String format(PlayerData playerData) {

                    StringBuilder sb = new StringBuilder();
                    switch (playerData.status) {

                        case ACCEPTED:
                            sb.append(ChatColor.GREEN);
                            break;
                        case DENIED:
                            sb.append(ChatColor.RED);
                            break;
                        default:
                            sb.append(ChatColor.AQUA);
                            break;
                    }
                    sb.append(playerData.name);
                    sb.append(ChatColor.GRAY).append(ChatColor.ITALIC).append(" - ");
                    sb.append(DATE_FORMAT.format(playerData.firstJoin)).append(" - ");
                    sb.append((playerData.unlocked == null ? ChatColor.RED + "Not Unlocked" : DATE_FORMAT.format(playerData.unlocked)));
                    return sb.toString();
                }
            }.display(sender, Database.getTable(GuestTable.class).getPlayers(args.getString(0)), args.getFlagInteger('p', 1));
        }
    }

    public class GuestTable extends Table {

        public GuestTable() {

            super("guests", "raidcraft_");
        }

        @Override
        public void createTable() {

            try {
                getConnection().prepareStatement(
                        "CREATE TABLE `" + getTableName() + "` (\n" +
                                "`id` INT NOT NULL AUTO_INCREMENT ,\n" +
                                "`player` VARCHAR( 32 ) NOT NULL ,\n" +
                                "`application_status` VARCHAR( 64 ) NOT NULL ,\n" +
                                "`application_processed` TIMESTAMP NULL , \n" +
                                "`unlocked` TIMESTAMP NULL , \n" +
                                "`first_join` TIMESTAMP NOT NULL , \n" +
                                "`last_join` TIMESTAMP NOT NULL , \n" +
                                "PRIMARY KEY ( `id` )\n" +
                                ")").execute();
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
        }

        public boolean exists(String player) {

            try {
                ResultSet resultSet = getConnection().prepareStatement(
                        "SELECT COUNT(*) as count FROM `" + getTableName() + "` WHERE player='" + player + "'").executeQuery();
                if (resultSet.next()) {
                    return resultSet.getInt("count") > 0;
                }
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
            return false;
        }

        public void addPlayer(String name) {

            if (exists(name)) {
                return;
            }
            try {
                getConnection().prepareStatement("INSERT INTO `" + getTableName() + "` " +
                        "(player, first_join, last_join, application_status) VALUES " +
                        "('" + name + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'UNKNOWN')").execute();
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
        }

        public PlayerData getPlayer(String name) {

            try {
                ResultSet resultSet = getConnection().prepareStatement(
                        "SELECT * FROM `" + getTableName() + "` WHERE player='" + name + "'").executeQuery();
                while (resultSet.next()) {
                    return new PlayerData(resultSet.getString("player"), resultSet);
                }
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        public List<PlayerData> getPlayers(String name) {

            List<PlayerData> playerDatas = new ArrayList<>();
            try {
                ResultSet resultSet = getConnection().prepareStatement(
                        "SELECT * FROM `" + getTableName() + "` WHERE player IS LIKE '%" + name + "%' ORDER BY last_join desc").executeQuery();
                while (resultSet.next()) {
                    playerDatas.add(new PlayerData(resultSet.getString("player"), resultSet));
                }
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
            return playerDatas;
        }

        public void unlockPlayer(String player) {

            try {
                getConnection().prepareStatement("UPDATE `" + getTableName() + "` " +
                        "SET (unlocked=CURRENT_TIMESTAMP) WHERE player='" + player + "'").execute();
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
        }

        public void updateLastJoin(String player) {

            try {
                getConnection().prepareStatement("UPDATE `" + getTableName() + "` " +
                        "SET (last_join=CURRENT_TIMESTAMP) WHERE player='" + player + "')").execute();
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public class PlayerData {

        public final String name;
        public final Timestamp firstJoin;
        public final Timestamp lastJoin;
        public final Timestamp unlocked;
        public final Timestamp applicationProcessed;
        public final ApplicationStatus status;

        public PlayerData(String name, ResultSet resultSet) throws SQLException {

            this.name = name;
            this.firstJoin = resultSet.getTimestamp("first_join");
            this.lastJoin = resultSet.getTimestamp("last_join");
            this.unlocked = resultSet.getTimestamp("unlocked");
            this.applicationProcessed = resultSet.getTimestamp("application_processed");
            this.status = ApplicationStatus.fromString(resultSet.getString("application_status"));
        }

        public boolean isAcceptedAndLocked() {

            return status == ApplicationStatus.ACCEPTED && unlocked == null;
        }

        public void unlock() {

            Database.getTable(GuestTable.class).unlockPlayer(name);

            Player player = Bukkit.getPlayer(name);
            if (player != null) {
                player.sendMessage(ChatColor.GREEN +
                        "Deine Bewerbung wurde soeben angenommen und du wurdest freigeschaltet!\n" +
                        "Viel Spass auf " + ChatColor.RED + "Raid-Craft.de!");
            }
        }

        public void updateLastJoin() {

            Database.getTable(GuestTable.class).updateLastJoin(name);
        }
    }

    public enum ApplicationStatus {

        UNKNOWN,
        ACCEPTED,
        DENIED;

        public static ApplicationStatus fromString(String status) {

            return EnumUtils.getEnumFromString(ApplicationStatus.class, status);
        }
    }
}
