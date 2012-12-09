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
    public final GuestComponent.ApplicationStatus status;

    public PlayerData(String name, ResultSet resultSet) throws SQLException {

        this.name = name;
        this.firstJoin = resultSet.getTimestamp("first_join");
        this.lastJoin = resultSet.getTimestamp("last_join");
        this.unlocked = resultSet.getTimestamp("unlocked");
        this.applicationProcessed = resultSet.getTimestamp("application_processed");
        this.status = GuestComponent.ApplicationStatus.fromString(resultSet.getString("application_status"));
    }

    public boolean isAcceptedAndLocked() {

        return status == GuestComponent.ApplicationStatus.ACCEPTED && unlocked == null;
    }

    public void unlock() {

        Database.getTable(GuestTable.class).unlockPlayer(name);

        // update the players group
        RaidCraft.getPermissions().playerAdd(GuestComponent.INST.config.main_world, name, "rcskills.levelsign");
        for (World world : Bukkit.getWorlds()) {
            RaidCraft.getPermissions().playerAddGroup(world, name, GuestComponent.INST.config.player_group);
        }

        final Player player = Bukkit.getPlayer(name);
        if (player != null) {
            player.sendMessage(ChatColor.GREEN +
                    "Deine Bewerbung wurde soeben angenommen und du wurdest freigeschaltet!\n" +
                    "Viel Spass auf " + ChatColor.RED + "Raid-Craft.de!");
            if (GuestComponent.INST.config.teleport_unlock && GuestComponent.INST.getTutorialSpawn() != null) {
                player.sendMessage(ChatColor.YELLOW + "Du wirst in Kürze in das Tutorial teleportiert.");
                Bukkit.getScheduler().scheduleSyncDelayedTask(CommandBook.inst(), new Runnable() {
                    @Override
                    public void run() {

                        player.teleport(GuestComponent.INST.getTutorialSpawn());
                    }
                }, 60L);
            }
        }
    }

    public void updateLastJoin() {

        Database.getTable(GuestTable.class).updateLastJoin(name);
    }
}