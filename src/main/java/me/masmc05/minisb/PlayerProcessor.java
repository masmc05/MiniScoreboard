package me.masmc05.minisb;

import com.google.common.collect.ImmutableSet;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.masmc05.minisb.lines.AnimatedLine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public record PlayerProcessor(Player player, PlayerBoard board, ScoreboardConfig config, AtomicLong now) implements TagResolver {
    public static final ImmutableSet<String> placeholders = ImmutableSet.of(
            "papi", "ping", "name", "display_name", "list_name", "x", "y", "z", "world", "tps",
            "world_namespace", "health", "max_health", "food", "time", "anim", "animation", "choice"
    );

    public PlayerProcessor(Player player, ScoreboardConfig config) {
        this(player, MiniScoreBoard.versionHelp.getBoard(player, config.lines().size()), config, new AtomicLong());
        board.title(config.title().processLine(this, 0));
        for (int i = 0; i < config.lines().size(); i++) {
            board.setLine(i, config.lines().get(i).processLine(this, 0));
        }
    }
    public synchronized void process(long tick) {
        if (tick % config.period() != 0) return;
        now.incrementAndGet();
        for (int i = 0; i < config.lines().size(); i++) {
            var line = config.lines().get(i);
            if (line.skip()) continue;
            board.setLine(i, line.processLine(this, now.get()));
        }
        if (!config.title().skip()) board.title(config.title().processLine(this, now.get()));
    }

    @Override
    public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
        var result = switch (name) {
            case "ping" -> Component.text(player.getPing());
            case "name" -> player.name();
            case "display_name" -> player.displayName();
            case "list_name" -> player.playerListName();
            case "x" -> Component.text(player.getLocation().getBlockX());
            case "y" -> Component.text(player.getLocation().getBlockY());
            case "z" -> Component.text(player.getLocation().getBlockZ());
            case "world" -> Component.text(player.getWorld().getName());
            case "world_namespace" -> Component.text(player.getWorld().key().asString());
            case "health" -> Component.text(player.getHealth());
            case "max_health" -> Component.text(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
            case "food" -> Component.text(player.getFoodLevel());
            case "time" -> Component.text(new SimpleDateFormat(Optional.ofNullable(arguments.peek()).map(Tag.Argument::value).orElse("HH:mm:ss.SSS")).format(new Date()));
            case "papi" -> {
                if (!MiniScoreBoard.papiIntegration) yield null;
                String s = arguments.popOr("PAPI needs the placeholder itself").value();
                TextType type = TextType.LEGACY;
                if (arguments.hasNext()) {
                    type = TextType.valueOf(s.toUpperCase(Locale.ROOT));
                    s = arguments.pop().value();
                }
                yield parse(player, s, type);
            }
            case "tps" -> {
                var format = "%.2f";
                var index = 0;
                if (arguments.hasNext()) {
                    var val = arguments.pop().value();
                    try {
                        index = Integer.parseInt(val);
                    } catch (NumberFormatException ignored) {
                        format = val;
                    }
                    if (arguments.hasNext()) {
                        format = arguments.pop().value();
                    }
                }
                yield Component.text(String.format(format, Bukkit.getTPS()[index]));
            }
            case "anim", "animation" -> {
                var first = arguments.popOr("Couldn't parse empty animation").value();
                int times = 1;
                if (arguments.hasNext()) {
                    try {
                        times = Integer.parseInt(first);
                        first = arguments.pop().value();
                    } catch (NumberFormatException ignored) {}
                }
                if (!arguments.hasNext()) {
                    var line = ScoreboardConfig.ANIMATED_LINE.get(first);
                    if (times != line.oncePerTimes()) line = new AnimatedLine(line.lines(), times);
                    yield line.processLine(this, now.get());
                }
                var list = new ArrayList<String>();
                list.add(first);
                while (arguments.hasNext()) list.add(arguments.pop().value());
                long index = list.size();
                index *= times;
                index = now.get() % index;
                index /= times;
                yield ctx.deserialize(list.get((int) index));
            }
            default -> null;
        };
        return result == null ? null : Tag.selfClosingInserting(result);
    }

    @Override
    public boolean has(@NotNull String name) {
        return placeholders.contains(name);
    }
    public Component parse(Player player, String text, TextType type) {
        if (text == null) return null;
        var split = text.split("_", 2);
        var expansion = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getExpansion(split[0]);
        if (expansion == null) return null;
        var result = switch (split.length) {
            case 1 -> expansion.onRequest(player, "");
            case 2 -> expansion.onRequest(player, split[1]);
            default -> throw new IllegalStateException();
        };
        if (result == null) return null;
        return type.parse(result, this);
    }

    public enum TextType {
        LEGACY {
            @Override
            public Component parse(String s, PlayerProcessor caller) {
                s = ChatColor.translateAlternateColorCodes('&', s);
                return LegacyComponentSerializer.legacySection().deserialize(s);
            }
        },
        MINI_MESSAGE {
            @Override
            public Component parse(String s, PlayerProcessor caller) {
                return MiniMessage.miniMessage().deserialize(s, caller);
            }
        },
        JSON {
            @Override
            public Component parse(String s, PlayerProcessor caller) {
                return GsonComponentSerializer.gson().deserialize(s);
            }
        };
        public abstract Component parse(String s, PlayerProcessor caller);
    }

}
