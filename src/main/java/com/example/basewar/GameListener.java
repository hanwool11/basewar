package com.example.basewar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getEntity();
        gameManager.handlePlayerDeath(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getPlayer();
        Team team = gameManager.getPlayerTeam(player);

        if (team != null && gameManager.getBeaconStatus().get(team)) {
            // 리스폰 위치를 비콘 바로 위로 설정
            Location beaconLocation = gameManager.getBeaconLocations().get(team);
            if (beaconLocation != null) {
                event.setRespawnLocation(beaconLocation.clone().add(0, 1, 0));
            }
        } else {
            // 최종 사망 처리된 플레이어는 관전 모드이므로 리스폰하지 않음
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 신호기 기반(철 블록) 파괴 방지
        if (block.getType() == Material.IRON_BLOCK) {
            Location above = block.getLocation().clone().add(0, 1, 0);
            if (gameManager.getBeaconLocations().containsValue(above)) {
                player.sendMessage("§c신호기 기반 블록은 파괴할 수 없습니다.");
                event.setCancelled(true);
                return;
            }
        }

        // 신호기 파괴 처리
        if (block.getType() == Material.BEACON) {
            Team brokenTeam = null;
            for (Team team : gameManager.getBeaconLocations().keySet()) {
                Location beaconLoc = gameManager.getBeaconLocations().get(team);
                if (beaconLoc != null && beaconLoc.equals(block.getLocation())) {
                    brokenTeam = team;
                    break;
                }
            }

            if (brokenTeam != null) {
                Team playerTeam = gameManager.getPlayerTeam(player);
                if (playerTeam == brokenTeam) {
                    player.sendMessage("§c자신의 팀 신호기는 파괴할 수 없습니다.");
                    event.setCancelled(true);
                    return;
                }
                gameManager.handleBeaconBreak(block, player);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() != Material.BEACON) return;

        Team placerTeam = gameManager.getPlayerTeam(player);
        if (placerTeam == null) return;

        // 팀의 신호기가 이미 설치되어 있는지 확인
        if (gameManager.getBeaconStatus().get(placerTeam)) {
            player.sendMessage("§c이미 당신의 팀 신호기가 설치되어 있습니다.");
            event.setCancelled(true);
            return;
        }

        // GameManager에 처리를 위임
        gameManager.handleBeaconPlace(block, player);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!gameManager.isGameInProgress()) return;

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player && victim instanceof Player) {
            Player attacker = (Player) damager;
            Player target = (Player) victim;

            Team attackerTeam = gameManager.getPlayerTeam(attacker);
            Team targetTeam = gameManager.getPlayerTeam(target);

            if (attackerTeam != null && attackerTeam == targetTeam) {
                event.setCancelled(true);
            }
        }
    }
}
