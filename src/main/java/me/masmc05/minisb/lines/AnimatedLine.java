package me.masmc05.minisb.lines;

import me.masmc05.minisb.PlayerProcessor;
import net.kyori.adventure.text.Component;

import java.util.List;

public record AnimatedLine(List<ScoreboardLine> lines, long oncePerTimes) implements ScoreboardLine {

    @Override
    public Component processLine(PlayerProcessor processor, long tick) {
        long index = lines.size();
        index *= oncePerTimes;
        index = tick % index;
        index /= oncePerTimes;
        return lines.get((int) index).processLine(processor, tick / oncePerTimes);
    }

    @Override
    public boolean skip() {
        return false;
    }
}
