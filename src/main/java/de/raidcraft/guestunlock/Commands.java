package de.raidcraft.guestunlock;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Database;
import de.raidcraft.util.LocationUtil;
import de.raidcraft.util.PaginatedResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Author: Philip
 * Date: 09.12.12 - 16:36
 * Description:
 */
public class Commands {

    public Commands(GuestUnlockPlugin module) {

    }

    @Command(
            aliases = {"tutorial", "tut"},
            desc = "Teleportiert den Spieler in das Tutorial",
            flags = "s"
    )
    public void tutorial(CommandContext args, CommandSender sender) throws CommandException {

        if (!(sender instanceof Player)) {
            return;
        }

        GuestUnlockPlugin plugin = RaidCraft.getComponent(GuestUnlockPlugin.class);

        if (args.hasFlag('s') && sender.hasPermission("tutorial.set")) {
            plugin.setTutorialSpawn(((Player) sender).getLocation());
            sender.sendMessage(ChatColor.GREEN + "Tutorial Spawn wurde an deine Position gesetzt.");
            return;
        }

        if (plugin.getTutorialSpawn() == null) {
            throw new CommandException("Der Tutorial Spawn ist noch nicht gesetzt.");
        }

        if (args.argsLength() > 0 && sender.hasPermission("tutorial.tp.other")) {
            Player targetPlayer = Bukkit.getPlayer(args.getString(0));
            if (targetPlayer == null) {
                throw new CommandException("Der gewählte Spieler wurde nicht gefunden!");
            }
            targetPlayer.teleport(plugin.getTutorialSpawn());
            sender.sendMessage(ChatColor.GREEN + "Du wurdest von einem Moderator zum " + ChatColor.AQUA + "Tutorial" + ChatColor.GREEN + " teleportiert.");
        }

        if (sender.hasPermission("raidcraft.player")
                && LocationUtil.getBlockDistance(((Player) sender).getLocation(), plugin.getTutorialSpawn()) > plugin.config.tutorial_range) {
            throw new CommandException("Du musst dich in " + plugin.config.tutorial_range + " Block Reichweite des Tutorials befinden.");
        } else {
            ((Player) sender).teleport(plugin.getTutorialSpawn());
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
                sb.append(GuestUnlockPlugin.DATE_FORMAT.format(playerData.firstJoin)).append(" - ");
                sb.append((playerData.unlocked == null ? ChatColor.RED + "Not Unlocked" : GuestUnlockPlugin.DATE_FORMAT.format(playerData.unlocked)));
                return sb.toString();
            }
        }.display(sender, Database.getTable(GuestTable.class).getPlayers(args.getString(0)), args.getFlagInteger('p', 1));
    }
}
