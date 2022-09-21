package me.masmc05.minisb.lines;

import me.masmc05.minisb.PlayerProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public record DynamicLine(String raw) implements ScoreboardLine{

    @Override
    public Component processLine(PlayerProcessor processor, long tick) {
        return MiniMessage.miniMessage().deserialize(raw, processor);
    }

    @Override
    public boolean skip() {
        return false;
    }
}
