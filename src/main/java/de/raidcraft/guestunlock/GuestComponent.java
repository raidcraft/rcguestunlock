package de.raidcraft.guestunlock;

import com.sk89q.commandbook.CommandBook;
import com.zachsthings.libcomponents.ComponentInformation;
import com.zachsthings.libcomponents.Depend;
import com.zachsthings.libcomponents.config.Setting;
import de.raidcraft.RaidCraft;
import de.raidcraft.RaidCraftPlugin;
import de.raidcraft.api.BasePlugin;
import de.raidcraft.api.Component;
import de.raidcraft.api.config.ConfigurationBase;
import de.raidcraft.api.database.Database;
import de.raidcraft.util.EnumUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Silthus
 */
@ComponentInformation(
        friendlyName = "Guest Component",
        desc = "Unlocks Guests when their application has been accepted."
)
@Depend(plugins = {"RaidCraft-API"})
public class GuestComponent extends BasePlugin implements Component, Listener {
    public static GuestComponent INST;
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private Set<String> players = new HashSet<>();
    public LocalConfiguration config;
    private Location tutorialSpawn = null;

    @Override
    public void enable() {
        INST = this;
        this.config = configure(new LocalConfiguration(this));

        registerCommands(Commands.class);
        CommandBook.registerEvents(this);
        new Database(RaidCraft.getComponent(RaidCraftPlugin.class)).registerTable(GuestTable.class, new GuestTable());

        // start a task that notifies players when their application was accepted
        Bukkit.getScheduler().scheduleSyncRepeatingTask(CommandBook.inst(), new Runnable() {
            @Override
            public void run() {

                GuestTable table = Database.getTable(GuestTable.class);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = table.getPlayer(player.getName());
                    if (data.isAcceptedAndLocked()) {
                        data.unlock();
                    } else if (player.hasPermission("raidcraft.player")) {
                        Database.getTable(GuestTable.class).unlockPlayer(player.getName());
                    }
                }
            }
        }, config.task_delay * 20, config.task_delay * 20);
    }

    @Override
    public void disable() {
        //TODO: implement
    }

    public boolean playerExists(String player) {
        return Database.getTable(GuestTable.class).exists(player);
    }

    public void setTutorialSpawn(Location location) {

        config.world = location.getWorld().getName();
        config.x = location.getX();
        config.y = location.getY();
        config.z = location.getZ();
        config.pitch = location.getPitch();
        config.yaw = location.getYaw();
        config.set("tutorial-spawn.world", location.getWorld().getName());
        config.set("tutorial-spawn.x", location.getX());
        config.set("tutorial-spawn.y", location.getY());
        config.set("tutorial-spawn.z", location.getZ());
        config.set("tutorial-spawn.pitch", location.getPitch());
        config.set("tutorial-spawn.yaw", location.getYaw());
        config.save();
        tutorialSpawn = getTutorialSpawn();
    }

    public Location getTutorialSpawn() {

        if (tutorialSpawn == null) {
            tutorialSpawn = new Location(Bukkit.getWorld(config.world),
                    config.x,
                    config.y,
                    config.z,
                    config.yaw,
                    config.pitch);
        }
        return tutorialSpawn;
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
            if (config.teleport_first_join && getTutorialSpawn() != null) {
                event.getPlayer().teleport(getTutorialSpawn());
            }
            // update the players permission groups
            event.setJoinMessage(ChatColor.AQUA + event.getPlayer().getName() + ChatColor.YELLOW + " ist das erste Mal auf Raid-Craft!");
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

    public static class LocalConfiguration extends ConfigurationBase<GuestComponent> {

        @Setting("task-delay")public int task_delay = 60;
        @Setting("main-world")public String main_world = "world";
        @Setting("guest-group")public String guest_group = "guest";
        @Setting("player-group")public String player_group = "player";
        @Setting("tutorial-spawn.world")public String world = "world";
        @Setting("tutorial-spawn.x")public double x = 37;
        @Setting("tutorial-spawn.y")public double y = 226;
        @Setting("tutorial-spawn.z")public double z = 58;
        @Setting("tutorial-spawn.pitch")public float pitch = 0F;
        @Setting("tutorial-spawn.yaw")public float yaw = 178.34F;
        @Setting("teleport-on-first-join")public boolean teleport_first_join = true;
        @Setting("teleport-on-unlock")public boolean teleport_unlock = false;
        @Setting("tutorial-range")public int tutorial_range = 500;

        public LocalConfiguration(GuestComponent plugin) {

            super(plugin, "config.yml");
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
