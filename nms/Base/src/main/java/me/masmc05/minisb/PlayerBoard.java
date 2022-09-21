package me.masmc05.minisb;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public interface PlayerBoard {
    void title(@NotNull Component displayName);

    @NonNull
    Player player();

    void setLine(int line, @NotNull Component component);

    void clear();
}
