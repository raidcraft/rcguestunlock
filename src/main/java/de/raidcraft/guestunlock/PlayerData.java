package de.raidcraft.guestunlock;

import de.raidcraft.RaidCraft;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Author: Philip
 * Date: 09.12.12 - 16:40
 * Description:
 */
@Entity
@Getter
@Setter
@Table(name = "raidcraft_guests")
public class PlayerData {

    public static boolean exists(UUID playerId) {

        PlayerData playerData = RaidCraft.getDatabase(GuestUnlockPlugin.class).find(PlayerData.class).where()
                .eq("player_id", playerId)
                .findUnique();
        return playerData != null;
    }

    public static void addPlayer(UUID playerId) {

        if (exists(playerId)) {
            return;
        }
        OfflinePlayer offlinePlayer = Bukkit.getPlayer(playerId);
        PlayerData playerData = new PlayerData();
        playerData.setPlayer(offlinePlayer.getName());
        playerData.setPlayerId(playerId);
        playerData.setFirstJoin(Timestamp.from(Instant.now()));
        playerData.setLastJoin(Timestamp.from(Instant.now()));
        playerData.setApplicationStatus(GuestUnlockPlugin.ApplicationStatus.UNKNOWN);
        RaidCraft.getDatabase(GuestUnlockPlugin.class).save(playerData);
    }

    public static PlayerData getPlayer(UUID playerId) {

        return RaidCraft.getDatabase(GuestUnlockPlugin.class).find(PlayerData.class).where()
                .eq("player_id", playerId).findUnique();
    }

    public static List<PlayerData> getPlayers(String name) {

        return RaidCraft.getDatabase(GuestUnlockPlugin.class).find(PlayerData.class).where()
                .like("player", name)
                .orderBy("last_join desc").findList();
    }

    @Id
    private int id;
    private String player;
    private UUID playerId;
    private Timestamp firstJoin;
    private Timestamp lastJoin;
    private Timestamp unlocked;
    private Timestamp applicationProcessed;
    private GuestUnlockPlugin.ApplicationStatus applicationStatus;
    private String forumPostUrl;

    public boolean isAcceptedAndLocked() {

        final GuestUnlockPlugin plugin = RaidCraft.getComponent(GuestUnlockPlugin.class);
        if (applicationStatus == GuestUnlockPlugin.ApplicationStatus.ACCEPTED && unlocked == null) {
            return true;
        }

        Player bukkitPlayer = Bukkit.getPlayer(playerId);
        if (applicationStatus == GuestUnlockPlugin.ApplicationStatus.ACCEPTED
                && bukkitPlayer != null && !bukkitPlayer.hasPermission("raidcraft.unlocked")) {
            return true;
        }

        return false;
    }

    public void unlock() {

        final GuestUnlockPlugin plugin = RaidCraft.getComponent(GuestUnlockPlugin.class);

        // update the players groups and unlock him in the skillsystem
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.getPlayer().isOnline()) {

            setUnlocked(Timestamp.from(Instant.now()));
            RaidCraft.getDatabase(GuestUnlockPlugin.class).update(this);

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
        setApplicationStatus(GuestUnlockPlugin.ApplicationStatus.ACCEPTED);
        setApplicationProcessed(Timestamp.from(Instant.now()));
        RaidCraft.getDatabase(GuestUnlockPlugin.class).update(this);
    }

    public void updateLastJoin() {

        setLastJoin(Timestamp.from(Instant.now()));
        RaidCraft.getDatabase(GuestUnlockPlugin.class).update(this);
    }
}