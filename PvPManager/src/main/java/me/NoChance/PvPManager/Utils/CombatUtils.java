package me.NoChance.PvPManager.Utils;

import me.NoChance.PvPManager.Config.Variables;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatUtils {

	private CombatUtils() {
	}

	public static boolean hasTimePassed(final long toggleTime, final int cooldown) {
		return System.currentTimeMillis() - toggleTime >= cooldown * 1000;
	}

	public static boolean isPvP(final EntityDamageByEntityEvent event) {
		final Entity attacker = event.getDamager();
		final Entity defender = event.getEntity();

		if (defender instanceof Player && !defender.hasMetadata("NPC")) {
			if (attacker instanceof Player && !attacker.hasMetadata("NPC"))
				return true;
			if (attacker instanceof Projectile) {
				final ProjectileSource projSource = ((Projectile) attacker).getShooter();
				if (projSource instanceof Player) {
					final Entity shooter = (Entity) projSource;
					if (!shooter.equals(defender) && !shooter.hasMetadata("NPC")) {
						return !(Variables.isIgnoreNoDamageHits() && event.getDamage() == 0);
					}
				}
			}
		}

		return false;
	}

	public static void fakeItemStackDrop(final Player player, final ItemStack[] inventory) {
		final Location playerLocation = player.getLocation();
		final World playerWorld = player.getWorld();
		for (final ItemStack itemstack : inventory) {
			if (itemstack != null && !itemstack.getType().equals(Material.AIR))
				playerWorld.dropItemNaturally(playerLocation, itemstack);
		}
	}

	public static boolean isOnline(final String name) {
		return Bukkit.getPlayer(name) != null;
	}

	public static boolean isWorldAllowed(final String worldName) {
		return !Variables.getWorldsExcluded().contains(worldName);
	}
}
