package com.example.basewar;

import org.bukkit.plugin.java.JavaPlugin;

public class BaseWarPlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        // Load and save config
        saveDefaultConfig();

        this.gameManager = new GameManager(this);

        // Register Commands
        this.getCommand("bw").setExecutor(new BwCommand(gameManager));
        this.getCommand("join").setExecutor(new JoinCommand(gameManager));

        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        getLogger().info("기지전쟁 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameInProgress()) {
            gameManager.stopGame(false);
        }
        getLogger().info("기지전쟁 플러그인이 비활성화되었습니다.");
    }
}
