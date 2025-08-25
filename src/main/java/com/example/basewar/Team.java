package com.example.basewar;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum Team {
    RED("Red", ChatColor.RED, Material.RED_STAINED_GLASS),
    BLUE("Blue", ChatColor.BLUE, Material.BLUE_STAINED_GLASS),
    YELLOW("Yellow", ChatColor.YELLOW, Material.YELLOW_STAINED_GLASS),
    GREEN("Green", ChatColor.GREEN, Material.LIME_STAINED_GLASS);

    private final String displayName;
    private final ChatColor chatColor;
    private final Material stainedGlass;

    Team(String displayName, ChatColor chatColor, Material stainedGlass) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.stainedGlass = stainedGlass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public Material getStainedGlass() {
        return stainedGlass;
    }
}