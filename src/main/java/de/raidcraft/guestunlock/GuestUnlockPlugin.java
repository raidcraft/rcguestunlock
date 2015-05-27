package de.raidcraft.guestunlock;

import com.avaje.ebean.annotation.EnumValue;
import de.raidcraft.RaidCraft;
import de.raidcraft.api.BasePlugin;
import de.raidcraft.api.config.ConfigurationBase;
import de.raidcraft.api.config.Setting;
import de.raidcraft.util.EnumUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Silthus
 */
public class GuestUnlockPlugin extends BasePlugin implements Listener {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private Set<String> players = new HashSet<>();
    public LocalConfiguration config;
    private Location tutorialSpawn = null;

    @Override
    public void enable() {

        this.config = configure(new LocalConfiguration(this));

        registerEvents(this);
        registerCommands(Commands.class);

        // start a task that notifies players when their application was accepted
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {

            for (Player player : Bukkit.getOnlinePlayers()) {
                checkForUnlock(player);
            }
        }, config.task_delay * 20, config.task_delay * 20);
    }

    private void checkForUnlock(Player player) {

        PlayerData data = PlayerData.getPlayer(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (data.isAcceptedAndLocked()) {
            data.unlock();
        } else if (data.getApplicationStatus() != ApplicationStatus.ACCEPTED && player.hasPermission("raidcraft.admin")) {
            data.unlock();
        }
    }

    @Override
    public void disable() {

    }

    @Override
    public List<Class<?>> getDatabaseClasses() {

        List<Class<?>> tables = new ArrayList<>();
        tables.add(PlayerData.class);
        return tables;
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
        UUID uuid = player.getUniqueId();
        File file = new File(event.getPlayer().getWorld().getWorldFolder(), "/playerdata/" + uuid + ".dat");
        if (!file.exists()) {
            players.add(name);
            RaidCraft.LOGGER.info("Player " + name + " joined the server the first time.");
        } else if (players.contains(name)) {
            players.remove(name);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {

        checkForUnlock(event.getPlayer());
        // lets set the permission group guest if this is his first join
        if (players.contains(event.getPlayer().getName())) {
            // teleport the player to the tutorial
            final UUID uniqueId = event.getPlayer().getUniqueId();
            if (config.teleport_first_join && getTutorialSpawn() != null) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // bukkit has a null reference of the player after joining
                    // we need to get the current and active reference
                    Player player = Bukkit.getPlayer(uniqueId);
                    if (player != null) player.teleport(getTutorialSpawn());
                }, 20L);
            }
            // update the players permission groups
            event.setJoinMessage(ChatColor.AQUA + event.getPlayer().getName() + ChatColor.YELLOW + " ist das erste Mal auf Raid-Craft!");
            players.remove(event.getPlayer().getName());
        }
        // lets generate that player in the database
        // the database will return if player already exists
        PlayerData.addPlayer(event.getPlayer().getUniqueId());
        PlayerData.getPlayer(event.getPlayer().getUniqueId()).updateLastJoin();
    }

    public static class LocalConfiguration extends ConfigurationBase<GuestUnlockPlugin> {

        @Setting("task-delay")
        public int task_delay = 60;
        @Setting("main-world")
        public String main_world = "world";
        @Setting("tutorial-spawn.world")
        public String world = "world";
        @Setting("tutorial-spawn.x")
        public double x = 37;
        @Setting("tutorial-spawn.y")
        public double y = 226;
        @Setting("tutorial-spawn.z")
        public double z = 58;
        @Setting("tutorial-spawn.pitch")
        public float pitch = 0F;
        @Setting("tutorial-spawn.yaw")
        public float yaw = 178.34F;
        @Setting("teleport-on-first-join")
        public boolean teleport_first_join = true;
        @Setting("teleport-on-unlock")
        public boolean teleport_unlock = false;
        @Setting("tutorial-range")
        public int tutorial_range = 500;
        @Setting("member-level")
        public int member_level = 2;

        public LocalConfiguration(GuestUnlockPlugin plugin) {

            super(plugin, "config.yml");
        }
    }

    public enum ApplicationStatus {

        @EnumValue("UNKNOWN")
        UNKNOWN,
        @EnumValue("ACCEPTED")
        ACCEPTED,
        @EnumValue("DENIED")
        DENIED;

        public static ApplicationStatus fromString(String status) {

            return EnumUtils.getEnumFromString(ApplicationStatus.class, status);
        }
    }
}
