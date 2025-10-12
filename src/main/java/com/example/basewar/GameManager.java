package com.example.basewar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final JavaPlugin plugin;
    private boolean gameInProgress = false;
    private boolean isInvincible = false;

    private final Map<Team, Set<UUID>> teams = new HashMap<>();
    private final Map<Team, Boolean> beaconStatus = new HashMap<>();
    private final Map<Team, Boolean> beaconEverPlaced = new HashMap<>();
    private final Map<Team, Location> beaconLocations = new HashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Map<UUID, Boolean> teamChatStatus = new HashMap<>();
    private final Map<UUID, BukkitTask> miningFatigueTasks = new HashMap<>();
    private final ScoreboardManager scoreboardManager;
    private Scoreboard mainScoreboard;
    private Location globalSpawnLocation;

    private int invincibilityDuration = 10;
    private BossBar invincibilityBossBar;
    private BukkitTask invincibilityTask;

    public static final String KIT_GUI_TITLE = "§x§0§0§8§0§f§f기본 아이템 설정";
    private List<ItemStack> kitItems = new ArrayList<>();

    public GameManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigData();
        for (Team team : Team.values()) {
            teams.put(team, new HashSet<>());
            beaconStatus.put(team, false);
            beaconEverPlaced.put(team, false);
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
        loadKit();
    }

    private void loadConfigData() {
        plugin.saveDefaultConfig(); // 만약 config.yml이 없다면 생성
        plugin.reloadConfig(); // 최신 설정 불러오기

        this.invincibilityDuration = plugin.getConfig().getInt("invincibility-duration", 10);

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

    public void openKitGui(Player player) {
        Inventory kitGui = Bukkit.createInventory(null, 54, KIT_GUI_TITLE);
        if (!kitItems.isEmpty()) {
            kitGui.setContents(kitItems.toArray(new ItemStack[0]));
        }
        player.openInventory(kitGui);
    }

    public void saveKit(Inventory inventory) {
        kitItems = Arrays.asList(inventory.getContents());
        plugin.getConfig().set("kit", kitItems);
        plugin.saveConfig();
    }

    @SuppressWarnings("unchecked")
    public void loadKit() {
        List<?> rawList = plugin.getConfig().getList("kit");
        if (rawList != null) {
            kitItems.clear();
            for (Object obj : rawList) {
                if (obj instanceof ItemStack) {
                    kitItems.add((ItemStack) obj);
                } else {
                    plugin.getLogger().warning("config.yml의 kit 목록에 잘못된 아이템이 있습니다.");
                }
            }
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

    public void setInvincibilityDuration(int duration) {
        this.invincibilityDuration = duration;
        plugin.getConfig().set("invincibility-duration", duration);
        plugin.saveConfig();
    }

    public boolean isInvincible() {
        return isInvincible;
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

        // 게임 시작 시 이전 보스바 정리
        if (invincibilityBossBar != null) {
            invincibilityBossBar.removeAll();
        }

        final Location startPoint;
        if (globalSpawnLocation != null) {
            startPoint = globalSpawnLocation;
        } else {
            startPoint = Bukkit.getWorlds().get(0).getSpawnLocation();
            Bukkit.broadcastMessage("§e[알림] §f게임 시작 스폰 지점이 설정되지 않아 기본 월드 스폰에서 시작합니다.");
        }

        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    Bukkit.broadcastMessage("§a게임 시작까지 " + countdown + "초 전!");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (countdown > 1) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        } else {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f); // Higher pitch for the final '1'
                        }
                    }
                    countdown--;
                } else {
                    this.cancel();
                    gameInProgress = true;
                    isInvincible = true;

                    // 모든 플레이어를 시작 지점으로 텔레포트하고 보스바에 추가
                    invincibilityBossBar = Bukkit.createBossBar("§a무적 시간", BarColor.GREEN, BarStyle.SEGMENTED_10);
                    invincibilityBossBar.setVisible(true);

                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        Team playerTeam = getPlayerTeam(onlinePlayer);
                        if (playerTeam != null) {
                            onlinePlayer.teleport(startPoint);
                            initializePlayer(onlinePlayer);
                            giveStartingKit(onlinePlayer);
                            invincibilityBossBar.addPlayer(onlinePlayer);
                        }
                    }

                    giveBeaconsToRandomPlayers();
                    Bukkit.broadcastMessage("§a기지전쟁 시작! " + invincibilityDuration + "초의 무적시간이 적용됩니다!");
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }

                    // 무적 시간 타이머 시작
                    startInvincibilityTimer();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startInvincibilityTimer() {
        invincibilityTask = new BukkitRunnable() {
            private int timer = invincibilityDuration;

            @Override
            public void run() {
                if (timer <= 0) {
                    isInvincible = false;
                    invincibilityBossBar.setVisible(false);
                    invincibilityBossBar.removeAll();
                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1.0f, 1.0f));
                    Bukkit.broadcastMessage("§c무적 시간이 종료되었습니다! 전투를 시작하세요!");
                    this.cancel();
                    return;
                }

                double progress = (double) timer / invincibilityDuration;
                invincibilityBossBar.setProgress(progress);

                int minutes = timer / 60;
                int seconds = timer % 60;
                String timeString = String.format("%d분 %d초", minutes, seconds);
                invincibilityBossBar.setTitle("§a무적 시간: §e" + timeString);

                if (progress <= 0.3) {
                    invincibilityBossBar.setColor(BarColor.RED);
                } else if (progress <= 0.6) {
                    invincibilityBossBar.setColor(BarColor.YELLOW);
                }

                timer--;
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
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void giveStartingKit(Player player) {
        // 기본 아이템 지급
        for (ItemStack item : kitItems) {
            if (item != null) {
                player.getInventory().addItem(item.clone());
            }
        }
    }

    public void addPlayerToTeam(Player player, Team team) {
        // 플레이어가 속해 있을 수 있는 모든 스코어보드 팀에서 명시적으로 제거
        org.bukkit.scoreboard.Team playerSbTeam = mainScoreboard.getEntryTeam(player.getName());
        if (playerSbTeam != null) {
            playerSbTeam.removeEntry(player.getName());
        }

        Team currentTeam = playerTeams.get(player.getUniqueId());

        // 게임이 진행 중이 아니면 자유롭게 팀에 참여/변경 허용
        if (!gameInProgress) {
            removePlayerFromCurrentTeam(player);
            teams.get(team).add(player.getUniqueId());
            playerTeams.put(player.getUniqueId(), team);

            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
            if (sbTeam != null) {
                sbTeam.addEntry(player.getName());
            }
            player.setScoreboard(mainScoreboard);
            player.setDisplayName(team.getChatColor() + player.getName() + ChatColor.RESET);
            player.setPlayerListName(team.getChatColor() + player.getName() + ChatColor.RESET);
            player.sendMessage(team.getChatColor() + team.getDisplayName() + " 팀" + team.getChatColor() + "에 참여했습니다.");
            return;
        }

        // 게임 진행 중인 경우:
        // 재접속 처리: 게임 진행 여부와 관계없이, 플레이어가 이미 팀에 속해있다면 스코어보드만 업데이트
        if (currentTeam == team) {
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
            if (sbTeam != null) {
                sbTeam.addEntry(player.getName());
            }
            player.setScoreboard(mainScoreboard);
            player.setDisplayName(team.getChatColor() + player.getName() + ChatColor.RESET);
            player.setPlayerListName(team.getChatColor() + player.getName() + ChatColor.RESET);
            return;
        }

        // 게임 중에 다른 팀으로 변경하려는 경우
        if (currentTeam != null) {
            player.sendMessage("§c게임 중에는 팀을 변경할 수 없습니다.");
            return;
        }

        // 게임 진행 중 처음 팀에 합류하는 경우
        removePlayerFromCurrentTeam(player);
        teams.get(team).add(player.getUniqueId());
        playerTeams.put(player.getUniqueId(), team);

        org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
        if (sbTeam != null) {
            sbTeam.addEntry(player.getName());
        }
        player.setScoreboard(mainScoreboard);
        player.setDisplayName(team.getChatColor() + player.getName() + ChatColor.RESET);
        player.setPlayerListName(team.getChatColor() + player.getName() + ChatColor.RESET);

        player.sendMessage(team.getChatColor() + team.getDisplayName() + " 팀" + team.getChatColor() + "에 참여했습니다.");
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
        teamChatStatus.remove(player.getUniqueId());
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
        beaconEverPlaced.put(placerTeam, true);
        beaconLocations.put(placerTeam, block.getLocation());
        Bukkit.broadcastMessage(placerTeam.getChatColor() + placerTeam.getDisplayName() + " 팀§f이 신호기를 설치했습니다! 이제부터 리스폰이 가능합니다.");

        // 팀원들에게 신호기 좌표 안내
        String coordsMessage = placerTeam.getChatColor() + "[팀 알림] §f당신의 팀 신호기가 X:" + block.getX() + ", Y:" + block.getY() + ", Z:" + block.getZ() + " 에 설치되었습니다!";
        for (UUID memberUUID : teams.get(placerTeam)) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline() && !member.equals(placer)) {
                member.sendMessage(coordsMessage);
            }
        }

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

        boolean hasBeacon = beaconStatus.get(team);
        boolean wasBeaconEverPlaced = beaconEverPlaced.getOrDefault(team, false);

        if (hasBeacon) {
            // 비콘이 있으면 onPlayerRespawn 이벤트에서 지연 리스폰 처리
            return;
        } else {
            if (wasBeaconEverPlaced) {
                // 비콘이 있었는데 파괴되었으면 최종 사망
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("신호기가 파괴되어 최종적으로 사망했습니다. 관전 모드로 전환됩니다.");
                checkForWinner();
            } else {
                // 비콘이 설치된 적 없으면 일반 리스폰
                player.sendMessage("아직 팀의 신호기가 설치되지 않았습니다. 잠시 후 리스폰됩니다.");
                player.setGameMode(GameMode.SURVIVAL); // Add this line
                // onPlayerRespawn 이벤트에서 글로벌 스폰으로 이동시킴
            }
        }
    }

    public void startRespawnCountdown(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) return;

        Location beaconLocation = beaconLocations.get(team);
        if (beaconLocation == null) { // 혹시 모를 예외 처리
            player.sendMessage("§c리스폰 위치를 찾을 수 없어 즉시 리스폰합니다.");
            initializePlayer(player);
            if(globalSpawnLocation != null) player.teleport(globalSpawnLocation);
            else player.teleport(player.getWorld().getSpawnLocation());
            return;
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(player.getLocation().add(0, 2, 0)); // 살짝 위에서 보도록

        int respawnTime = 5; // 5초 고정

        new BukkitRunnable() {
            int timer = respawnTime;

            @Override
            public void run() {
                if (timer > 0) {
                    player.sendTitle("§c사망!", "§e" + timer + "초 후 리스폰됩니다.", 0, 25, 5);
                    timer--;
                } else {
                    this.cancel();
                    player.setGameMode(GameMode.SURVIVAL);
                    initializePlayer(player);
                    // 텔레포트 후 짧은 지연을 주어 클라이언트 업데이트를 돕습니다.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.teleport(beaconLocation.clone().add(0.5, 1, 0.5)); // 블록 중앙으로 텔레포트
                        }
                    }.runTaskLater(plugin, 1L);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void checkForWinner() {
        List<Team> teamsWithRemainingPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR && getPlayerTeam(p) != null)
                .map(this::getPlayerTeam)
                .distinct()
                .collect(Collectors.toList());

        if (teamsWithRemainingPlayers.size() == 1) {
            Team winner = teamsWithRemainingPlayers.get(0);
            String title = winner.getChatColor() + winner.getDisplayName() + " 팀 승리";
            String subtitle = "§7최종 승리 했습니다.";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(title, subtitle, 10, 70, 20); // fadeIn, stay, fadeOut ticks
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            stopGame(false);
        } else if (teamsWithRemainingPlayers.isEmpty() && gameInProgress) {
            Bukkit.broadcastMessage("§c모든 팀이 탈락하여 무승부입니다!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            }
            stopGame(false);
        }
    }

    private void resetGame() {
        gameInProgress = false;
        isInvincible = false;

        // 무적 시간 타이머 및 보스바 정리
        if (invincibilityTask != null && !invincibilityTask.isCancelled()) {
            invincibilityTask.cancel();
        }
        if (invincibilityBossBar != null) {
            invincibilityBossBar.setVisible(false);
            invincibilityBossBar.removeAll();
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setScoreboard(scoreboardManager.getMainScoreboard());
        }

        for (org.bukkit.scoreboard.Team sbTeam : mainScoreboard.getTeams()) {
            sbTeam.unregister();
        }

        // 초기화 후 스코어보드 팀 다시 등록
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

        teams.clear();
        playerTeams.clear();
        beaconLocations.clear();
        beaconEverPlaced.clear();
        teamChatStatus.clear();

        // 채굴 피로 효과 정리
        for (UUID playerUUID : miningFatigueTasks.keySet()) {
            BukkitTask task = miningFatigueTasks.get(playerUUID);
            task.cancel();
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            }
        }
        miningFatigueTasks.clear();

        for (Team team : Team.values()) {
            teams.put(team, new HashSet<>());
            beaconStatus.put(team, false);
            beaconEverPlaced.put(team, false);
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

    public void toggleTeamChat(Player player) {
        boolean currentStatus = teamChatStatus.getOrDefault(player.getUniqueId(), false);
        teamChatStatus.put(player.getUniqueId(), !currentStatus);
        if (!currentStatus) {
            player.sendMessage("§a팀 채팅이 활성화되었습니다.");
        } else {
            player.sendMessage("§c팀 채팅이 비활성화되었습니다.");
        }
    }

    public boolean isTeamChatEnabled(UUID playerUUID) {
        return teamChatStatus.getOrDefault(playerUUID, false);
    }

    public Set<UUID> getTeamMembers(Team team) {
        return teams.get(team);
    }

    public Location getGlobalSpawnLocation() {
        return globalSpawnLocation;
    }

    public void startBeaconMining(Player player, Block beaconBlock) {
        // 이미 작업이 실행중이면 중복 실행 방지
        if (miningFatigueTasks.containsKey(player.getUniqueId())) {
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    stopBeaconMining(player);
                    return;
                }

                // 플레이어가 바라보는 블록이 대상 비콘이 아니면 중지
                Block targetBlock = player.getTargetBlock(null, 5);
                if (targetBlock == null || !targetBlock.equals(beaconBlock)) {
                    stopBeaconMining(player);
                    return;
                }

                // 채굴 피로 효과 적용 (1.3초, 2단계, 파티클 없음)
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 26, 0, true, false));
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 반복

        miningFatigueTasks.put(player.getUniqueId(), task);
    }

    public void stopBeaconMining(Player player) {
        if (miningFatigueTasks.containsKey(player.getUniqueId())) {
            miningFatigueTasks.get(player.getUniqueId()).cancel();
            miningFatigueTasks.remove(player.getUniqueId());
        }
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    public BossBar getInvincibilityBossBar() {
        return invincibilityBossBar;
    }
}