package de.raidcraft.guestunlock;

import com.sk89q.commandbook.CommandBook;
import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Author: Philip
 * Date: 09.12.12 - 16:40
 * Description:
 */
public class PlayerData {

    public final String name;
    public final Timestamp firstJoin;
    public final Timestamp lastJoin;
    public final Timestamp unlocked;
    public final Timestamp applicationProcessed;
    public final GuestUnlockPlugin.ApplicationStatus status;

    public PlayerData(String name, ResultSet resultSet) throws SQLException {

        this.name = name;
        this.firstJoin = resultSet.getTimestamp("first_join");
        this.lastJoin = resultSet.getTimestamp("last_join");
        this.unlocked = resultSet.getTimestamp("unlocked");
        this.applicationProcessed = resultSet.getTimestamp("application_processed");
        this.status = GuestUnlockPlugin.ApplicationStatus.fromString(resultSet.getString("application_status"));
    }

    public boolean isAcceptedAndLocked() {

        return status == GuestUnlockPlugin.ApplicationStatus.ACCEPTED && unlocked == null;
    }

    public void unlock() {

        final GuestUnlockPlugin plugin = RaidCraft.getComponent(GuestUnlockPlugin.class);
        Database.getTable(GuestTable.class).unlockPlayer(name);

        // update the players group
        RaidCraft.getPermissions().playerAdd(plugin.config.main_world, name, "rcskills.levelsign");
        for (World world : Bukkit.getWorlds()) {
            RaidCraft.getPermissions().playerAddGroup(world, name, plugin.config.player_group);
        }

        final Player player = Bukkit.getPlayer(name);
        if (player != null) {
            player.sendMessage(ChatColor.GREEN +
                    "Deine Bewerbung wurde soeben angenommen und du wurdest freigeschaltet!\n" +
                    "Viel Spass auf " + ChatColor.RED + "Raid-Craft.de!");
            if (plugin.config.teleport_unlock && plugin.getTutorialSpawn() != null) {
                player.sendMessage(ChatColor.YELLOW + "Du wirst in Kürze in das Tutorial teleportiert.");
                Bukkit.getScheduler().scheduleSyncDelayedTask(CommandBook.inst(), new Runnable() {
                    @Override
                    public void run() {

                        player.teleport(plugin.getTutorialSpawn());
                    }
                }, 60L);
            }
        }
    }

    public void updateLastJoin() {

        Database.getTable(GuestTable.class).updateLastJoin(name);
    }
}