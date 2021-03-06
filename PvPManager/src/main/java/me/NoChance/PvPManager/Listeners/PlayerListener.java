package me.NoChance.PvPManager.Listeners;

import me.NoChance.PvPManager.Config.Messages;
import me.NoChance.PvPManager.Config.Variables;
import me.NoChance.PvPManager.Config.Variables.DropMode;
import me.NoChance.PvPManager.Managers.PlayerHandler;
import me.NoChance.PvPManager.PvPlayer;
import me.NoChance.PvPManager.Utils.CombatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class PlayerListener implements Listener {

	private final PlayerHandler ph;

	public PlayerListener(final PlayerHandler ph) {
		this.ph = ph;
	}

	@EventHandler
	public final void onBlockPlace(final BlockPlaceEvent event) {
		if (Variables.isBlockPlaceBlocks() && ph.get(event.getPlayer()).isInCombat())
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public final void onPlayerLogout(final PlayerQuitEvent event) {
		final Player player = event.getPlayer();
		final PvPlayer pvPlayer = ph.get(player);
		if (pvPlayer.isInCombat()) {
			if (Variables.isLogToFile())
				ph.getPlugin().getLog().log(player.getName() + " tried to escape combat and got punished!");
			for (final String s : Variables.getCommandsOnPvPLog())
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ChatColor.translateAlternateColorCodes('&', s.replace("%p", player.getName())));
			if (Variables.isPunishmentsEnabled())
				ph.applyPunishments(pvPlayer);
		}
		ph.remove(pvPlayer);
	}

	@SuppressWarnings("null")
	@EventHandler(priority = EventPriority.HIGHEST)
	public final void onPlayerDeath(final PlayerDeathEvent event) {
		final Player player = event.getEntity();
		if (!CombatUtils.isWorldAllowed(player.getWorld().getName()))
			return;
		final PvPlayer pvPlayer = ph.get(player);
		if (pvPlayer.isInCombat())
			ph.untag(pvPlayer);

		// Let's process player's inventory/exp according to config file
		if (pvPlayer.hasPvPLogged()) {
			if (!Variables.isDropExp()) {
				event.setKeepLevel(true);
				event.setDroppedExp(0);
			}
			if (!Variables.isDropInventory() && Variables.isDropArmor()) {
				CombatUtils.fakeItemStackDrop(player, player.getInventory().getArmorContents());
				player.getInventory().setArmorContents(null);
			} else if (Variables.isDropInventory() && !Variables.isDropArmor()) {
				CombatUtils.fakeItemStackDrop(player, player.getInventory().getContents());
				player.getInventory().clear();
			}
			if (!Variables.isDropInventory() || !Variables.isDropArmor())
				event.setKeepInventory(true);
		}

		final Player killer = player.getKiller();
		final boolean pvpDeath = killer != null;
		// Player died in combat, process that
		if (pvpDeath && !killer.equals(player)) {
			final PvPlayer pKiller = ph.get(killer);
			if (pKiller == null)
				return;
			if (Variables.isKillAbuseEnabled())
				pKiller.addVictim(player.getName());
			if (Variables.getMoneyReward() > 0)
				pKiller.giveReward(player);
			if (Variables.getMoneyPenalty() > 0)
				pvPlayer.applyPenalty();
			if (Variables.isCommandsOnKillEnabled())
				for (final String command : Variables.getCommandsOnKill()) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", killer.getName()).replace("<victim>", player.getName()));
				}
		}
		if (!pvPlayer.hasPvPLogged()) {
			final DropMode mode = Variables.getDropMode();
			switch (mode) {
			case DROP:
				if (!pvpDeath)
					event.setKeepInventory(true);
				break;
			case KEEP:
				if (pvpDeath)
					event.setKeepInventory(true);
				break;
			case TRANSFER:
				if (pvpDeath) {
					final ItemStack[] drops = event.getDrops().toArray(new ItemStack[event.getDrops().size()]);
					final HashMap<Integer, ItemStack> returned = killer.getInventory().addItem(drops);
					CombatUtils.fakeItemStackDrop(player, returned.values().toArray(new ItemStack[returned.values().size()]));
					event.getDrops().clear();
				}
				break;
			default:
				break;
			}
		}
	}

	@EventHandler
	public final void onPlayerInteract(final PlayerInteractEvent e) { // NO_UCD
		final Player player = e.getPlayer();
		if (CombatUtils.isWorldAllowed(player.getWorld().getName())) {
			final ItemStack i = player.getItemInHand();
			if (Variables.isAutoSoupEnabled() && i.getType() == Material.MUSHROOM_SOUP) {
				if (player.getHealth() == player.getMaxHealth())
					return;
				player.setHealth(player.getHealth() + Variables.getSoupHealth() > player.getMaxHealth() ? player.getMaxHealth() : player.getHealth() + Variables.getSoupHealth());
				i.setType(Material.BOWL);
				return;
			}
			final PvPlayer pvplayer = ph.get(player);
			if (pvplayer == null)
				return;
			if ((i.getType().equals(Material.FLINT_AND_STEEL) || i.getType().equals(Material.LAVA_BUCKET)) && e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
				for (final Player p : e.getClickedBlock().getWorld().getPlayers()) {
					if (e.getPlayer().equals(p))
						continue;
					final PvPlayer target = ph.get(p);
					if ((!target.hasPvPEnabled() || !pvplayer.hasPvPEnabled()) && e.getClickedBlock().getLocation().distanceSquared(p.getLocation()) < 9) {
						pvplayer.message(Messages.pvpDisabledOther(target.getName()));
						e.setCancelled(true);
						return;
					}
				}
			}
		}
	}

	@EventHandler
	public final void onPlayerPickup(final PlayerPickupItemEvent e) { // NO_UCD
		if (Variables.isNewbieProtectionEnabled() && Variables.isBlockPickNewbies()) {
			final PvPlayer player = ph.get(e.getPlayer());
			if (player.isNewbie())
				e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public final void onPlayerJoin(final PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		ph.get(player).updatePlayer(player);
		if (player.isOp() || player.hasPermission("pvpmanager.admin")) {
			if (Variables.isUpdate())
				Messages.updateMessage(player);
			if (Variables.isConfigUpdated())
				Messages.configUpdated(player);
		}
	}

	@EventHandler
	public final void onPlayerKick(final PlayerKickEvent event) {
		final PvPlayer pvPlayer = ph.get(event.getPlayer());
		if (pvPlayer.isInCombat() && Variables.isPunishmentsEnabled() && !Variables.punishOnKick()) {
			ph.untag(pvPlayer);
		}
	}

	@EventHandler
	public final void onPlayerTeleport(final PlayerTeleportEvent event) {
		final PvPlayer player = ph.get(event.getPlayer());
		if (player != null && Variables.isInCombatEnabled() && player.isInCombat()) {
			if (event.getCause().equals(TeleportCause.ENDER_PEARL) && Variables.isBlockEnderPearl()) {
				event.setCancelled(true);
				player.message(Messages.getEnderpearlBlockedIncombat());
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public final void onCommand(final PlayerCommandPreprocessEvent event) { // NO_UCD
		if (Variables.isStopCommands() && Variables.isInCombatEnabled()) {
			if (ph.get(event.getPlayer()).isInCombat()) {
				boolean contains = false;
				final String[] givenCommand = event.getMessage().substring(1).split(" ", 3);
				for (int i = 0; i < givenCommand.length; i++) {
					String args = givenCommand[0];
					for (int j = 1; j <= i; j++) {
						args += " " + givenCommand[j];
					}
					if (Variables.getCommandsAllowed().contains(args.toLowerCase())) {
						contains = true;
						break;
					}
				}
				if (Variables.isCommandsWhitelist() ? !contains : contains) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(Messages.getCommandDeniedIncombat());
				}
			}
		}
	}

	@EventHandler
	public final void onPlayerRespawn(final PlayerRespawnEvent event) { // NO_UCD
		if (CombatUtils.isWorldAllowed(event.getPlayer().getWorld().getName())) {
			if (Variables.isKillAbuseEnabled() && Variables.getRespawnProtection() != 0) {
				final PvPlayer player = ph.get(event.getPlayer());
				if (player == null)
					return;
				player.setRespawnTime(System.currentTimeMillis());
			}
		}
	}

}
