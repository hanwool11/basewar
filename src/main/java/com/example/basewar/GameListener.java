package com.example.basewar;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GameManager.KIT_GUI_TITLE)) {
            Player player = (Player) event.getPlayer();
            if (player.hasPermission("basewar.admin")) {
                gameManager.saveKit(event.getInventory());
                player.sendMessage("§a기본 아이템 설정이 저장되었습니다.");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!gameManager.isGameInProgress()) return;

        Player player = event.getPlayer();
        Team team = gameManager.getPlayerTeam(player);

        if (gameManager.isTeamChatEnabled(player.getUniqueId())) {
            if (team == null) return;

            event.setCancelled(true);

            String teamMessage = team.getChatColor() + "[팀] " + player.getName() + "§f: " + event.getMessage();

            Set<UUID> teamMembers = gameManager.getTeamMembers(team);
            for (UUID memberUUID : teamMembers) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    member.sendMessage(teamMessage);
                }
            }
        } else {
            // 일반 채팅
            String format;
            if (team != null) {
                format = team.getChatColor() + "%1$s§f: %2$s"; // 팀 색상 적용
            } else {
                format = "§f%1$s§f: %2$s"; // 팀이 없으면 기본 흰색
            }
            event.setFormat(format);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team team = gameManager.getPlayerTeam(player);
        if (team != null) {
            gameManager.addPlayerToTeam(player, team);
        }

        // 재접속 시 무적 보스바에 다시 추가
        if (gameManager.isGameInProgress() && gameManager.isInvincible() && gameManager.getInvincibilityBossBar() != null) {
            gameManager.getInvincibilityBossBar().addPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 플레이어가 나가면 채굴 피로 효과 제거
        gameManager.stopBeaconMining(event.getPlayer());
    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getEntity();

        // 사망 화면이 뜨도록 하고, 아이템 드롭은 기본 동작에 맡김
        gameManager.handlePlayerDeath(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getPlayer();
        Team team = gameManager.getPlayerTeam(player);

        // 비콘이 살아있으면 리스폰 대기시간 시작
        if (team != null && gameManager.getBeaconStatus().get(team)) {
            // 즉시 리스폰되는 것을 막기 위해 현재 위치로 리스폰 위치 설정
            event.setRespawnLocation(player.getLocation());
            gameManager.startRespawnCountdown(player);
            return;
        }

        // 그 외의 경우 (팀이 없거나, 비콘이 없거나, 비콘이 파괴되었지만 아직 최종 탈락은 아닌 경우)
        // 최종 사망 처리는 handlePlayerDeath에서 하므로, 여기서는 리스폰 위치만 지정
        if (player.getGameMode() != GameMode.SPECTATOR) {
            Location globalSpawn = gameManager.getGlobalSpawnLocation();
            if (globalSpawn != null) {
                event.setRespawnLocation(globalSpawn);
            } else {
                event.setRespawnLocation(player.getWorld().getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (!gameManager.isGameInProgress()) return;
        if (event.getBlock().getType() != Material.BEACON) {
            // 다른 블록을 캐기 시작하면 이전의 채굴 피로 효과 제거
            gameManager.stopBeaconMining(event.getPlayer());
            return;
        }

        // 무적 시간에는 비콘 손상 불가
        if (gameManager.isInvincible()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        Team playerTeam = gameManager.getPlayerTeam(player);
        Team beaconTeam = null;
        for (Team team : gameManager.getBeaconLocations().keySet()) {
            if (event.getBlock().getLocation().equals(gameManager.getBeaconLocations().get(team))) {
                beaconTeam = team;
                break;
            }
        }

        // 자신의 팀 비콘은 해당 없음
        if (beaconTeam == null || playerTeam == beaconTeam) {
            gameManager.stopBeaconMining(player);
            return;
        }

        // 적 팀 비콘을 캘 때 효과 시작
        gameManager.startBeaconMining(player, event.getBlock());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 비콘을 파괴하면 채굴 피로 효과 즉시 중지
        if (block.getType() == Material.BEACON) {
            if (gameManager.isInvincible()) {
                player.sendMessage("§c무적 시간에는 신호기를 파괴할 수 없습니다.");
                event.setCancelled(true);
                return;
            }

            gameManager.stopBeaconMining(player);

            // 성급함 버프 적용 (1.3초, 1단계, 파티클 없음)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 0, true, false));

            // 신호기 아이템 드롭 방지
            event.setDropItems(false);
        }

        if (block.getType() == Material.IRON_BLOCK) {
            Location above = block.getLocation().clone().add(0, 1, 0);
            if (gameManager.getBeaconLocations().containsValue(above)) {
                player.sendMessage("§c신호기 기반 블록은 파괴할 수 없습니다.");
                event.setCancelled(true);
                return;
            }
        }

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

        if (gameManager.getBeaconStatus().get(placerTeam)) {
            player.sendMessage("§c이미 당신의 팀 신호기가 설치되어 있습니다.");
            event.setCancelled(true);
            return;
        }

        gameManager.handleBeaconPlace(block, player);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameManager.isGameInProgress() || !(event.getEntity() instanceof Player)) {
            return;
        }

        // 무적 시간에는 모든 데미지 방지
        if (gameManager.isInvincible()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!gameManager.isGameInProgress()) return;

        // 무적 시간 체크는 onEntityDamage에서 처리하므로 여기서는 아군 공격만 확인
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

    // New explosion handlers
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!gameManager.isGameInProgress()) return;
        removeBeaconsFromExplosion(event.blockList());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!gameManager.isGameInProgress()) return;
        removeBeaconsFromExplosion(event.blockList());
    }

    private void removeBeaconsFromExplosion(List<Block> blockList) {
        Set<Location> protectedLocations = new HashSet<>();

        // Add all registered beacon locations and their 3x3 iron bases to the protected set
        for (Location beaconLoc : gameManager.getBeaconLocations().values()) {
            if (beaconLoc != null) {
                protectedLocations.add(beaconLoc); // Protect the beacon itself

                // Protect the 3x3 iron block base below the beacon
                int baseX = beaconLoc.getBlockX();
                int baseY = beaconLoc.getBlockY() - 1;
                int baseZ = beaconLoc.getBlockZ();

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        protectedLocations.add(new Location(beaconLoc.getWorld(), baseX + x, baseY, baseZ + z));
                    }
                }
            }
        }

        // Remove protected blocks from the explosion list
        blockList.removeIf(block -> protectedLocations.contains(block.getLocation()));
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getPlayer();

        // 관전 모드일 때 움직임 제한
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // 플레이어가 블록을 이동했는지 확인 (시야만 움직이는 것은 허용)
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
            }
        }
    }
}