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
import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Database;
import de.raidcraft.api.database.Table;
import de.raidcraft.util.EnumUtils;
import de.raidcraft.util.LocationUtil;
import de.raidcraft.util.PaginatedResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Silthus
 */
@ComponentInformation(
        friendlyName = "Guest Component",
        desc = "Unlocks Guests when their application has been accepted."
)
@Depend(plugins = {"RaidCraft-API"})
public class GuestComponent extends BukkitComponent implements Listener {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private Set<String> players = new HashSet<>();
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {

        Player player = event.getPlayer();
        String name = player.getName();
        File file = new File(Bukkit.getWorldContainer(), config.main_world + "/players/" + name + ".dat");
        if (!file.exists()) {
            players.add(name);
            CommandBook.logger().info("Player " + name + " joined the server the first time.");
        } else if (players.contains(name)) {
            players.remove(name);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {

        // lets set the permission group guest if this is his first join
        if (players.contains(event.getPlayer().getName())) {
            // teleport the player to the tutorial
            if (config.teleport_first_join && config.tutorial_spawn != null) {
                event.getPlayer().teleport(config.tutorial_spawn);
            }
            // update the players permission groups
            event.setJoinMessage(event.getPlayer().getName() + " ist das erste Mal auf Raid-Craft!");
            for (World world : Bukkit.getWorlds()) {
                RaidCraft.getPermissions().playerAddGroup(world, event.getPlayer().getName(), config.guest_group);
            }
            players.remove(event.getPlayer().getName());
        }
        // lets generate that player in the database
        // the database will return if player already exists
        Database.getTable(GuestTable.class).addPlayer(event.getPlayer().getName());
        Database.getTable(GuestTable.class).getPlayer(event.getPlayer().getName()).updateLastJoin();
    }

    public static class LocalConfiguration extends ConfigurationBase {

        @Setting("task-delay")public int task_delay = 60;
        @Setting("main-world")public String main_world = "world";
        @Setting("guest-group")public String guest_group = "guest";
        @Setting("player-group")public String player_group = "player";
        @Setting("tutorial-spawn")public Location tutorial_spawn;
        @Setting("teleport-on-first-join")public boolean teleport_first_join = true;
        @Setting("teleport-on-unlock")public boolean teleport_unlock = false;
        @Setting("tutorial-range")public int tutorial_range = 500;
    }

    public class Commands {

        @Command(
                aliases = {"tutorial", "tut"},
                desc = "Teleportiert den Spieler in das Tutorial",
                flags = "s"
        )
        public void tutorial(CommandContext args, CommandSender sender) throws CommandException {

            if (!(sender instanceof Player)) {
                return;
            }

            if (args.hasFlag('s') && sender.hasPermission("tutorial.set")) {
                config.tutorial_spawn = ((Player) sender).getLocation();
                sender.sendMessage(ChatColor.GREEN + "Tutorial Spawn wurde an deine Position gesetzt.");
                return;
            }

            if (config.tutorial_spawn == null) {
                throw new CommandException("Der Tutorial Spawn ist noch nicht gesetzt.");
            }

            if (sender.hasPermission("raidcraft.player")
                    && LocationUtil.getBlockDistance(((Player) sender).getLocation(), config.tutorial_spawn) > config.tutorial_range) {
                throw new CommandException("Du musst dich in " + config.tutorial_range + " Block Reichweite des Tutorials befinden.");
            } else {
                ((Player) sender).teleport(config.tutorial_spawn);
                sender.sendMessage(ChatColor.GREEN + "Du wurdest zum " + ChatColor.AQUA + "Tutorial" + ChatColor.GREEN + " teleportiert.");
            }
        }

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
                                "`application_status` VARCHAR( 64 ) NOT NULL DEFAULT 'UNKNOWN' ,\n" +
                                "`application_processed` TIMESTAMP NULL DEFAULT NULL , \n" +
                                "`unlocked` TIMESTAMP NULL DEFAULT NULL , \n" +
                                "`first_join` TIMESTAMP NOT NULL , \n" +
                                "`last_join` TIMESTAMP NOT NULL , \n" +
                                "`forum_post_url` VARCHAR ( 256 ) NULL DEFAULT NULL , \n" +
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
                        "SET unlocked=CURRENT_TIMESTAMP WHERE player='" + player + "'").execute();
                // update the players group
                for (World world : Bukkit.getWorlds()) {
                    RaidCraft.getPermissions().playerAddGroup(world, player, config.player_group);
                }
            } catch (SQLException e) {
                CommandBook.logger().severe(e.getMessage());
                e.printStackTrace();
            }
        }

        public void updateLastJoin(String player) {

            try {
                getConnection().prepareStatement("UPDATE `" + getTableName() + "` " +
                        "SET last_join=CURRENT_TIMESTAMP WHERE player='" + player + "')").execute();
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

            final Player player = Bukkit.getPlayer(name);
            if (player != null) {
                player.sendMessage(ChatColor.GREEN +
                        "Deine Bewerbung wurde soeben angenommen und du wurdest freigeschaltet!\n" +
                        "Viel Spass auf " + ChatColor.RED + "Raid-Craft.de!");
                if (config.teleport_unlock && config.tutorial_spawn != null) {
                    player.sendMessage(ChatColor.YELLOW + "Du wirst in Kürze in das Tutorial teleportiert.");
                    Bukkit.getScheduler().scheduleSyncDelayedTask(CommandBook.inst(), new Runnable() {
                        @Override
                        public void run() {

                            player.teleport(config.tutorial_spawn);
                        }
                    }, 60L);
                }
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
