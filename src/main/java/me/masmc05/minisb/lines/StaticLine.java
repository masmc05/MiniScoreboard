package me.masmc05.minisb.lines;

import me.masmc05.minisb.PlayerProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public record StaticLine(Component component) implements ScoreboardLine {

    public StaticLine(String line) {
        this(MiniMessage.miniMessage().deserialize(line));
    }

    @Override
    public Component processLine(PlayerProcessor processor, long tick) {
        return component;
    }

    @Override
    public boolean skip() {
        return true;
    }
}
