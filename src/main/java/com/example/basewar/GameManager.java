package com.example.basewar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final JavaPlugin plugin;
    private boolean gameInProgress = false;

    private final Map<Team, Set<UUID>> teams = new HashMap<>();
    private final Map<Team, Boolean> beaconStatus = new HashMap<>();
    private final Map<Team, Location> beaconLocations = new HashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final ScoreboardManager scoreboardManager;
    private Scoreboard mainScoreboard;
    private Location globalSpawnLocation;

    public GameManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigData();
        for (Team team : Team.values()) {
            teams.put(team, new HashSet<>());
            beaconStatus.put(team, false);
        }

        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.mainScoreboard = scoreboardManager.getMainScoreboard();

        for (Team teamEnum : Team.values()) {
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(teamEnum.name());
            if (sbTeam == null) {
                sbTeam = mainScoreboard.registerNewTeam(teamEnum.name());
            }
            sbTeam.setPrefix(teamEnum.getChatColor() + "");
            sbTeam.setColor(teamEnum.getChatColor());
            sbTeam.setCanSeeFriendlyInvisibles(false);
            sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        }
    }

    private void loadConfigData() {
        plugin.saveDefaultConfig(); // 만약 config.yml이 없다면 생성
        plugin.reloadConfig(); // 최신 설정 불러오기

        ConfigurationSection teamsSection = plugin.getConfig().getConfigurationSection("teams");
        if (teamsSection == null) {
            plugin.getLogger().severe("config.yml에 'teams' 설정이 없습니다! 플러그인을 비활성화합니다.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        for (String teamName : teamsSection.getKeys(false)) {
            try {
                Team.valueOf(teamName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("'" + teamName + "'은(는) 유효한 팀 이름이 아닙니다. (config.yml)");
            }
        }

        ConfigurationSection spawnSection = plugin.getConfig().getConfigurationSection("global-spawn");
        if (spawnSection != null) {
            this.globalSpawnLocation = getLocationFromConfig(spawnSection);
        }
    }

    public void setGlobalSpawn(Location location) {
        this.globalSpawnLocation = location;
        plugin.getConfig().set("global-spawn.World", location.getWorld().getName());
        plugin.getConfig().set("global-spawn.X", location.getX());
        plugin.getConfig().set("global-spawn.Y", location.getY());
        plugin.getConfig().set("global-spawn.Z", location.getZ());
        plugin.getConfig().set("global-spawn.YAW", location.getYaw());
        plugin.getConfig().set("global-spawn.PITCH", location.getPitch());
        plugin.saveConfig();
    }

    private Location getLocationFromConfig(ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("World", "world"));
        if (world == null) {
            plugin.getLogger().severe("설정 파일에 지정된 월드 '" + section.getString("World") + "'를 찾을 수 없습니다.");
            return null;
        }
        double x = section.getDouble("X");
        double y = section.getDouble("Y");
        double z = section.getDouble("Z");
        float yaw = (float) section.getDouble("YAW", 0.0);
        float pitch = (float) section.getDouble("PITCH", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void startGame() {
        if (gameInProgress) {
            Bukkit.broadcastMessage("게임이 이미 진행 중입니다.");
            return;
        }

        final Location startPoint;
        if (globalSpawnLocation != null) {
            startPoint = globalSpawnLocation;
        } else {
            // Bukkit.getWorlds().get(0)는 기본 월드를 의미합니다.
            startPoint = Bukkit.getWorlds().get(0).getSpawnLocation();
            Bukkit.broadcastMessage("§e[알림] §f게임 시작 스폰 지점이 설정되지 않아 기본 월드 스폰에서 시작합니다.");
        }

        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    Bukkit.broadcastMessage("§a게임 시작까지 " + countdown + "초 전!");
                    countdown--;
                } else {
                    gameInProgress = true;

                    // 모든 플레이어를 시작 지점으로 텔레포트
                    for (Set<UUID> teamMembers : teams.values()) {
                        for (UUID playerUUID : teamMembers) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null) {
                                player.teleport(startPoint);
                                initializePlayer(player);
                            }
                        }
                    }

                    giveBeaconsToRandomPlayers();
                    Bukkit.broadcastMessage("§a기지전쟁 시작! 신호기를 안전한 곳에 설치하고 적의 신호기를 파괴하세요!");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void giveBeaconsToRandomPlayers() {
        for (Team team : teams.keySet()) {
            Set<UUID> teamMembers = teams.get(team);
            if (teamMembers.isEmpty()) continue;

            List<Player> onlineMembers = teamMembers.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toList());

            if (onlineMembers.isEmpty()) {
                plugin.getLogger().warning(team.getDisplayName() + " 팀에 온라인 상태인 플레이어가 없어 신호기를 지급할 수 없습니다.");
                continue;
            }

            Player luckyPlayer = onlineMembers.get(new Random().nextInt(onlineMembers.size()));
            luckyPlayer.getInventory().addItem(new ItemStack(Material.BEACON));
            luckyPlayer.sendMessage("§a당신은 팀의 신호기를 받았습니다! 안전한 위치에 설치하세요.");
        }
    }

    public void stopGame(boolean forced) {
        if (!gameInProgress) {
            Bukkit.broadcastMessage("게임이 진행 중이 아닙니다.");
            return;
        }
        for (Location beaconLoc : beaconLocations.values()) {
            if (beaconLoc != null && beaconLoc.getBlock().getType() == Material.BEACON) {
                beaconLoc.getBlock().setType(Material.AIR);
            }
        }
        resetGame();
        if (forced) {
            Bukkit.broadcastMessage("§c게임이 강제 종료되었습니다.");
        }
    }

    private void initializePlayer(Player player) {
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
    }

    public void addPlayerToTeam(Player player, Team team) {
        if (gameInProgress && playerTeams.containsKey(player.getUniqueId())) {
            player.sendMessage("§c게임 중에는 팀을 변경할 수 없습니다.");
            return;
        }

        removePlayerFromCurrentTeam(player);
        teams.get(team).add(player.getUniqueId());
        playerTeams.put(player.getUniqueId(), team);

        org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
        if (sbTeam != null) {
            sbTeam.addEntry(player.getName());
        }
        player.setScoreboard(mainScoreboard);

        player.sendMessage(team.getChatColor() + team.getDisplayName() + " 팀§f에 참여했습니다.");
    }

    private void removePlayerFromCurrentTeam(Player player) {
        Team currentTeam = playerTeams.get(player.getUniqueId());
        if (currentTeam != null) {
            teams.get(currentTeam).remove(player.getUniqueId());
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(currentTeam.name());
            if (sbTeam != null) {
                sbTeam.removeEntry(player.getName());
            }
        }
        playerTeams.remove(player.getUniqueId());
    }

    public void handleBeaconBreak(Block block, Player breaker) {
        if (block.getType() != Material.BEACON) return;

        Team brokenTeam = null;
        for (Map.Entry<Team, Location> entry : beaconLocations.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(block.getLocation())) {
                brokenTeam = entry.getKey();
                break;
            }
        }

        if (brokenTeam == null) return;

        Team breakerTeam = getPlayerTeam(breaker);
        if (breakerTeam == brokenTeam) {
            breaker.sendMessage("자신의 팀 신호기는 파괴할 수 없습니다.");
            return;
        }

        if (beaconStatus.get(brokenTeam)) {
            beaconStatus.put(brokenTeam, false);
            beaconLocations.remove(brokenTeam);
            Bukkit.broadcastMessage(brokenTeam.getChatColor() + brokenTeam.getDisplayName() + " 팀§f의 신호기가 파괴되었습니다! 이제 해당 팀은 리스폰할 수 없습니다.");

            Location beaconLoc = block.getLocation();
            World world = beaconLoc.getWorld();
            Material glassType = brokenTeam.getStainedGlass();

            // 주변 유리 블록 제거
            Block[] glassBlocks = {
                beaconLoc.clone().add(0, 1, 0).getBlock(), // Top
                beaconLoc.clone().add(1, 0, 0).getBlock(),  // East
                beaconLoc.clone().add(-1, 0, 0).getBlock(), // West
                beaconLoc.clone().add(0, 0, 1).getBlock(),  // South
                beaconLoc.clone().add(0, 0, -1).getBlock()  // North
            };
            for (Block glassBlock : glassBlocks) {
                if (glassBlock.getType() == glassType) {
                    glassBlock.setType(Material.AIR);
                }
            }

            // 하단 철 블록 제거
            int baseX = beaconLoc.getBlockX();
            int baseY = beaconLoc.getBlockY() - 1;
            int baseZ = beaconLoc.getBlockZ();

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block platformBlock = world.getBlockAt(baseX + x, baseY, baseZ + z);
                    if (platformBlock.getType() == Material.IRON_BLOCK) {
                        platformBlock.setType(Material.AIR);
                    }
                }
            }

            checkForWinner();
        }
    }

    public void handleBeaconPlace(Block block, Player placer) {
        Team placerTeam = getPlayerTeam(placer);
        if (placerTeam == null) return;

        if (beaconStatus.get(placerTeam)) {
            placer.sendMessage("§c이미 당신의 팀 신호기가 설치되어 있습니다.");
            return;
        }

        beaconStatus.put(placerTeam, true);
        beaconLocations.put(placerTeam, block.getLocation());
        Bukkit.broadcastMessage(placerTeam.getChatColor() + placerTeam.getDisplayName() + " 팀§f이 신호기를 설치했습니다! 이제부터 리스폰이 가능합니다.");

        // 신호기 아래 철 블록 및 주변 유리 블록 설치
        Location beaconLoc = block.getLocation();
        World world = beaconLoc.getWorld();
        Material glassType = placerTeam.getStainedGlass();

        // 철 블록 설치
        int baseX = beaconLoc.getBlockX();
        int baseY = beaconLoc.getBlockY() - 1;
        int baseZ = beaconLoc.getBlockZ();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.IRON_BLOCK);
            }
        }

        // 팀 색깔 유리 블록 설치
        beaconLoc.clone().add(0, 1, 0).getBlock().setType(glassType); // Top
        beaconLoc.clone().add(1, 0, 0).getBlock().setType(glassType);  // East
        beaconLoc.clone().add(-1, 0, 0).getBlock().setType(glassType); // West
        beaconLoc.clone().add(0, 0, 1).getBlock().setType(glassType);  // South
        beaconLoc.clone().add(0, 0, -1).getBlock().setType(glassType); // North
    }

    public void handlePlayerDeath(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) return;

        if (beaconStatus.get(team)) {
            player.sendMessage("신호기가 아직 살아있습니다. 잠시 후 리스폰됩니다.");
        } else {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage("신호기가 파괴되어 최종적으로 사망했습니다. 관전 모드로 전환됩니다.");
            checkForWinner();
        }
    }

    private void checkForWinner() {
        List<Team> teamsWithRemainingPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR && getPlayerTeam(p) != null)
                .map(this::getPlayerTeam)
                .distinct()
                .collect(Collectors.toList());

        if (teamsWithRemainingPlayers.size() == 1) {
            Team winner = teamsWithRemainingPlayers.get(0);
            Bukkit.broadcastMessage("§6§l" + winner.getDisplayName() + " 팀이 최종 승리했습니다!");
            stopGame(false);
        } else if (teamsWithRemainingPlayers.isEmpty() && gameInProgress) {
            Bukkit.broadcastMessage("§c모든 팀이 탈락하여 무승부입니다!");
            stopGame(false);
        }
    }

    private void resetGame() {
        gameInProgress = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setScoreboard(scoreboardManager.getMainScoreboard());
        }

        for (org.bukkit.scoreboard.Team sbTeam : mainScoreboard.getTeams()) {
            for (String entry : sbTeam.getEntries()) {
                sbTeam.removeEntry(entry);
            }
        }

        teams.clear();
        playerTeams.clear();
        beaconLocations.clear();
        for (Team team : Team.values()) {
            teams.put(team, new HashSet<>());
            beaconStatus.put(team, false);
        }
    }

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    public Team getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public Map<Team, Location> getBeaconLocations() {
        return beaconLocations;
    }

    public Map<Team, Boolean> getBeaconStatus() {
        return beaconStatus;
    }
}