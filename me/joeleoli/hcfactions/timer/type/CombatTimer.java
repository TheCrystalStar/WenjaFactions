package me.joeleoli.hcfactions.timer.type;

import com.doctordark.util.BukkitUtils;
import com.doctordark.util.DurationFormatter;
import com.google.common.base.Optional;
import me.joeleoli.hcfactions.FactionsPlugin;
import me.joeleoli.hcfactions.faction.event.PlayerClaimEnterEvent;
import me.joeleoli.hcfactions.faction.event.PlayerLeaveFactionEvent;
import me.joeleoli.hcfactions.timer.event.TimerClearEvent;
import me.joeleoli.hcfactions.faction.event.PlayerJoinFactionEvent;
import me.joeleoli.hcfactions.timer.PlayerTimer;
import me.joeleoli.hcfactions.timer.event.TimerStartEvent;
import me.joeleoli.hcfactions.visualise.VisualType;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Timer used to tag {@link Player}s in combat to prevent entering safe-zones.
 */
public class CombatTimer extends PlayerTimer implements Listener {

	private FactionsPlugin plugin;

	public CombatTimer(FactionsPlugin plugin) {
		super("Combat Tag", TimeUnit.SECONDS.toMillis(30L));
		this.plugin = plugin;
	}

	@Override
	public String getScoreboardPrefix() {
		return ChatColor.RED.toString() + ChatColor.BOLD;
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onTimerStop(TimerClearEvent event) {
		if (event.getTimer() == this) {
			Optional<UUID> optionalUserUUID = event.getUserUUID();

			if (optionalUserUUID.isPresent()) {
				this.onExpire(optionalUserUUID.get());
			}
		}
	}

	@Override
	public void onExpire(UUID userUUID) {
		Player player = Bukkit.getPlayer(userUUID);

		if (player != null) {
			plugin.getVisualiseHandler().clearVisualBlocks(player, VisualType.SPAWN_BORDER, null);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onFactionJoin(PlayerJoinFactionEvent event) {
		Optional<Player> optional = event.getPlayer();

		if (optional.isPresent()) {
			Player player = optional.get();
			long remaining = getRemaining(player);

			if (remaining > 0L) {
				event.setCancelled(true);
				player.sendMessage(ChatColor.RED + "You cannot join factions whilst your " + getDisplayName() + ChatColor.RED + " timer is active [" + ChatColor.BOLD + DurationFormatter.getRemaining(getRemaining(player), true, false) + ChatColor.RED + " remaining]");
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onFactionLeave(PlayerLeaveFactionEvent event) {
		if (event.isForce()) return;

		Optional<Player> optional = event.getPlayer();

		if (optional.isPresent()) {
			Player player = optional.get();
			long remaining = this.getRemaining(player);

			if (remaining > 0L) {
				event.setCancelled(true);

				CommandSender sender = event.getSender();

				if (sender == player) {
					sender.sendMessage(ChatColor.RED + "Cannot kick " + player.getName() + " as their " + getDisplayName() + ChatColor.RED + " timer is active [" + ChatColor.BOLD + DurationFormatter.getRemaining(remaining, true, false) + ChatColor.RED + " remaining]");
				} else {
					sender.sendMessage(ChatColor.RED + "You cannot leave factions whilst your " + getDisplayName() + ChatColor.RED + " timer is active [" + ChatColor.BOLD + DurationFormatter.getRemaining(remaining, true, false) + ChatColor.RED + " remaining]");
				}
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPreventClaimEnter(PlayerClaimEnterEvent event) {
		if (event.getEnterCause() == PlayerClaimEnterEvent.EnterCause.TELEPORT) return;

		Player player = event.getPlayer();

		if (!event.getFromFaction().isSafezone() && event.getToFaction().isSafezone() && getRemaining(player) > 0L) {
			event.setCancelled(true);
			player.sendMessage(ChatColor.RED + "You cannot enter " + event.getToFaction().getDisplayName(player) + ChatColor.RED + " whilst your " + getDisplayName() + ChatColor.RED + " timer is active [" + ChatColor.BOLD + DurationFormatter.getRemaining(getRemaining(player), true, false) + ChatColor.RED + " remaining]");
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		Player attacker = BukkitUtils.getFinalAttacker(event, true);
		Entity entity;

		if (attacker != null && (entity = event.getEntity()) instanceof Player) {
			Player attacked = (Player) entity;
			setCooldown(attacker, attacker.getUniqueId(), defaultCooldown, false);
			setCooldown(attacked, attacked.getUniqueId(), defaultCooldown, false);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onTimerStart(TimerStartEvent event) {
		if (event.getTimer() == this) {
			Optional<Player> optional = event.getPlayer();

			if (optional.isPresent()) {
				Player player = optional.get();
				player.sendMessage(ChatColor.YELLOW + "You are now combat tagged for " + ChatColor.RED + DurationFormatUtils.formatDurationWords(event.getDuration(), true, true) + ChatColor.YELLOW + '.');
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		clearCooldown(event.getPlayer().getUniqueId());
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPreventClaimEnterMonitor(PlayerClaimEnterEvent event) {
		if ((event.getEnterCause() == PlayerClaimEnterEvent.EnterCause.TELEPORT) && (!event.getFromFaction().isSafezone() && event.getToFaction().isSafezone())) {
			clearCooldown(event.getPlayer());
		}
	}

}