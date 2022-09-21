package me.masmc05.minisb;

import org.bukkit.entity.Player;

public interface VersionHelp {
    boolean checkCompatible();

    PlayerBoard getBoard(Player player, int lines);
}
