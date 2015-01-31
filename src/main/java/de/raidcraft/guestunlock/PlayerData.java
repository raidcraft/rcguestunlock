package de.raidcraft.guestunlock;

import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Database;
import de.raidcraft.skills.SkillsPlugin;
import de.raidcraft.skills.api.hero.Hero;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Author: Philip
 * Date: 09.12.12 - 16:40
 * Description:
 */
public class PlayerData {

    public final UUID playerId;
    public final Timestamp firstJoin;
    public final Timestamp lastJoin;
    public final Timestamp unlocked;
    public final Timestamp applicationProcessed;
    public final GuestUnlockPlugin.ApplicationStatus status;

    public PlayerData(UUID playerId, ResultSet resultSet) throws SQLException {

        this.playerId = playerId;
        this.firstJoin = resultSet.getTimestamp("first_join");
        this.lastJoin = resultSet.getTimestamp("last_join");
        this.unlocked = resultSet.getTimestamp("unlocked");
        this.applicationProcessed = resultSet.getTimestamp("application_processed");
        this.status = GuestUnlockPlugin.ApplicationStatus.fromString(resultSet.getString("application_status"));
    }

    public boolean isAcceptedAndLocked() {

        final GuestUnlockPlugin plugin = RaidCraft.getComponent(GuestUnlockPlugin.class);
        if (status == GuestUnlockPlugin.ApplicationStatus.ACCEPTED && unlocked == null) {
            return true;
        }
        Hero hero = RaidCraft.getComponent(SkillsPlugin.class).getCharacterManager()
                .getHero(playerId);
        if (status == GuestUnlockPlugin.ApplicationStatus.ACCEPTED
                && hero.getVirtualProfession().getAttachedLevel().getLevel() < plugin.config.member_level) {
            return true;
        }

        return false;
    }

    public void unlock() {

        final GuestUnlockPlugin plugin = RaidCraft.getComponent(GuestUnlockPlugin.class);

        // update the players groups and unlock him in the skillsystem
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.getPlayer().isOnline()) {

            Hero hero = RaidCraft.getComponent(SkillsPlugin.class).getCharacterManager()
                    .getHero(p.getUniqueId());
            Database.getTable(GuestTable.class).unlockPlayer(playerId);
            hero.getVirtualProfession().getAttachedLevel().setLevel(plugin.config.member_level);

            final Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.GREEN +
                        "Deine Bewerbung wurde soeben angenommen und du wurdest freigeschaltet!\n" +
                        "Viel Spass auf " + ChatColor.RED + "Raid-Craft.de!");
                if (plugin.config.teleport_unlock && plugin.getTutorialSpawn() != null) {
                    player.sendMessage(ChatColor.YELLOW + "Du wirst in Kürze in das Tutorial teleportiert.");
                    Bukkit.getScheduler().scheduleSyncDelayedTask(RaidCraft.getComponent(GuestUnlockPlugin.class), new Runnable() {
                        @Override
                        public void run() {

                            player.teleport(plugin.getTutorialSpawn());
                        }
                    }, 60L);
                }
            }
        }
        // player is offline
        Database.getTable(GuestTable.class).acceptPlayer(playerId);
    }

    public void updateLastJoin() {

        Database.getTable(GuestTable.class).updateLastJoin(playerId);
    }
}