package de.raidcraft.guestunlock;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import de.raidcraft.RaidCraft;
import de.raidcraft.api.database.Database;
import de.raidcraft.api.player.UnknownPlayerException;
import de.raidcraft.skills.SkillsPlugin;
import de.raidcraft.skills.api.hero.Hero;
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

    private final GuestUnlockPlugin plugin;

    public Commands(GuestUnlockPlugin plugin) {

        this.plugin = plugin;
    }

    @Command(
            aliases = {"tutorial", "tut"},
            desc = "Teleportiert den Spieler in das Tutorial",
            flags = "s"
    )
    @CommandPermissions("raidcraft.player")
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
            if(!sender.hasPermission("tutorial.tp.other.all") && targetPlayer.getLocation().getWorld() != plugin.getTutorialSpawn().getWorld()) {
                throw new CommandException("Der Spieler muss sich auf der Hauptwelt befinden!");
            }
            targetPlayer.teleport(plugin.getTutorialSpawn());
            sender.sendMessage(ChatColor.GREEN + "Du wurdest von einem Moderator zum " + ChatColor.AQUA + "Tutorial" + ChatColor.GREEN + " teleportiert.");
        }

        if (sender.hasPermission("raidcraft.player")
                && LocationUtil.getBlockDistance(((Player) sender).getLocation(), plugin.getTutorialSpawn()) > plugin.config.tutorial_range) {
            throw new CommandException("Du musst dich in " + plugin.config.tutorial_range + " Block Reichweite des Tutorials befinden.");
        }
    }

    @Command(
            aliases = {"gast", "unlock", "gunlock", "gu", "guest"},
            desc = "Schaltet Spieler nach erfolgreicher Bewerbung frei.",
            usage = "<player>",
            flags = "f",
            min = 1
    )
    @CommandPermissions("guestunlock.unlock")
    public void unlock(CommandContext args, CommandSender sender) throws CommandException {

        try {
            PlayerData player = Database.getTable(GuestTable.class).getPlayer(args.getString(0));
            if (!args.hasFlag('f') && player == null) {
                throw new CommandException("Es gibt keinen Spieler mit dem Namen: " + args.getString(0));
            }
            // create a new player entry
            if (player == null && args.hasFlag('f')) {
                Database.getTable(GuestTable.class).addPlayer(args.getString(0));
                player = Database.getTable(GuestTable.class).getPlayer(args.getString(0));
            }
            Hero hero = RaidCraft.getComponent(SkillsPlugin.class).getCharacterManager().getHero(player.name);
            if (player.unlocked != null && hero.getVirtualProfession().getAttachedLevel().getLevel() >= plugin.config.member_level) {
                throw new CommandException("Der Spieler wurde bereits freigeschaltet.");
            }
            player.unlock();
            sender.sendMessage(ChatColor.GREEN + "Der Spieler " + ChatColor.AQUA + player + ChatColor.GREEN + " wurde freigeschaltet.");
        } catch (UnknownPlayerException e) {
            throw new CommandException(e.getMessage());
        }
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
