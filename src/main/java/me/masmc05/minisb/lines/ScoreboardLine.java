package me.masmc05.minisb.lines;

import me.masmc05.minisb.PlayerProcessor;
import net.kyori.adventure.text.Component;

public interface ScoreboardLine {

    Component processLine(PlayerProcessor processor, long tick);

    boolean skip();
}
