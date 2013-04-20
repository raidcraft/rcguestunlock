package de.raidcraft.guestunlock;

import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    public boolean exists(String player) {

        try {
            ResultSet resultSet = executeQuery(
                    "SELECT COUNT(*) as count FROM `" + getTableName() + "` WHERE player='" + player + "'");
            if (resultSet.next()) {
                return resultSet.getInt("count") > 0;
            }
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void addPlayer(String name) {

        if (exists(name)) {
            return;
        }
        try {
            executeUpdate("INSERT INTO `" + getTableName() + "` " +
                    "(player, first_join, last_join, application_status) VALUES " +
                    "('" + name + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'UNKNOWN')");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public PlayerData getPlayer(String name) {

        try {
            ResultSet resultSet = executeQuery(
                    "SELECT * FROM `" + getTableName() + "` WHERE player='" + name + "'");
            if (resultSet.next()) {
                return new PlayerData(resultSet.getString("player"), resultSet);
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
                playerDatas.add(new PlayerData(resultSet.getString("player"), resultSet));
            }
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
        return playerDatas;
    }

    public void unlockPlayer(String player) {

        try {
            executeUpdate("UPDATE `" + getTableName() + "` " +
                    "SET unlocked=CURRENT_TIMESTAMP WHERE player='" + player + "'");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public void acceptPlayer(String player) {

        try {
            executeUpdate("UPDATE `" + getTableName() + "` " +
                    "SET application_status='ACCEPTED', application_processed=CURRENT_TIMESTAMP " +
                    "WHERE player='" + player + "'"
            );
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateLastJoin(String player) {

        try {
            executeUpdate("UPDATE `" + getTableName() + "` " +
                    "SET last_join=CURRENT_TIMESTAMP WHERE player='" + player + "'");
        } catch (SQLException e) {
            RaidCraft.LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }
}