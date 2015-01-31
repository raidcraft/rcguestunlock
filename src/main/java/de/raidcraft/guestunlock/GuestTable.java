package de.raidcraft.guestunlock;

import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Table;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Author: Philip
 * Date: 09.12.12 - 16:37
 * Description:
 */
public class GuestTable extends Table {

    public GuestTable() {

        super("guests", "raidcraft_");
    }

    @Override
    public void createTable() {

        try {
            executeUpdate("CREATE TABLE `" + getTableName() + "` (\n" +
                    "`id` INT NOT NULL AUTO_INCREMENT ,\n" +
                    "`player` VARCHAR( 32 ) NOT NULL ,\n" +
                    "`player_id` VARCHAR( 40 ) NOT NULL ,\n" +
                    "`application_status` VARCHAR( 64 ) NOT NULL DEFAULT 'UNKNOWN' ,\n" +
                    "`application_processed` TIMESTAMP NULL DEFAULT NULL , \n" +
                    "`unlocked` TIMESTAMP NULL DEFAULT NULL , \n" +
                    "`first_join` TIMESTAMP NOT NULL , \n" +
                    "`last_join` TIMESTAMP NOT NULL , \n" +
                    "`forum_post_url` VARCHAR ( 256 ) NULL DEFAULT NULL , \n" +
                    "PRIMARY KEY ( `id` )\n" +
                    ")");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean exists(UUID playerId) {

        try {
            ResultSet resultSet = executeQuery(
                    "SELECT COUNT(*) as count FROM `" + getTableName() + "` WHERE player_id='" + playerId + "'");
            if (resultSet.next()) {
                return resultSet.getInt("count") > 0;
            }
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void addPlayer(UUID playerId) {

        if (exists(playerId)) {
            return;
        }
        try {
            OfflinePlayer playerData = Bukkit.getPlayer(playerId);
            executeUpdate("INSERT INTO `" + getTableName() + "` " +
                    "(player_id, player, first_join, last_join, application_status) VALUES " +
                    "('" + playerId + "','" + playerData.getName() + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'UNKNOWN')");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public PlayerData getPlayer(UUID playerId) {

        try {
            ResultSet resultSet = executeQuery(
                    "SELECT * FROM `" + getTableName() + "` WHERE player_id='" + playerId + "'");
            if (resultSet.next()) {
                return new PlayerData(UUID.fromString(resultSet.getString("player_id")), resultSet);
            }
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<PlayerData> getPlayers(String name) {

        List<PlayerData> playerDatas = new ArrayList<>();
        try {
            ResultSet resultSet = executeQuery(
                    "SELECT * FROM `" + getTableName() + "` WHERE player LIKE '" + name + "%' ORDER BY last_join desc");
            while (resultSet.next()) {
                playerDatas.add(new PlayerData(UUID.fromString(resultSet.getString("player_id")), resultSet));
            }
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
        return playerDatas;
    }

    public void unlockPlayer(UUID playerId) {

        try {
            executeUpdate("UPDATE `" + getTableName() + "` " +
                    "SET unlocked=CURRENT_TIMESTAMP WHERE player_id='" + playerId + "'");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public void acceptPlayer(UUID playerId) {

        try {
            executeUpdate("UPDATE `" + getTableName() + "` " +
                            "SET application_status='ACCEPTED', application_processed=CURRENT_TIMESTAMP " +
                            "WHERE player_id='" + playerId + "'"
            );
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateLastJoin(UUID playerId) {

        try {
            executeUpdate("UPDATE `" + getTableName() + "` " +
                    "SET last_join=CURRENT_TIMESTAMP WHERE player_id='" + playerId + "'");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }
}