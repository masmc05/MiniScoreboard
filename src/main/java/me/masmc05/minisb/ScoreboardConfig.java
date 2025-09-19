package me.masmc05.minisb;

import me.masmc05.minisb.lines.AnimatedLine;
import me.masmc05.minisb.lines.DynamicLine;
import me.masmc05.minisb.lines.ScoreboardLine;
import me.masmc05.minisb.lines.StaticLine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.internal.parser.node.TagNode;
import net.kyori.adventure.text.minimessage.internal.parser.node.TagPart;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.apache.commons.lang3.Validate;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record ScoreboardConfig(List<Predicate<Player>> contexts, List<ScoreboardLine> lines, ScoreboardLine title, int period) {

    public static final Map<String, AnimatedLine> ANIMATED_LINE = new ConcurrentHashMap<>();
    public static final List<ScoreboardConfig> CONFIGS = new CopyOnWriteArrayList<>();
    public static final HashMap<String, ContextProvider> providers = new HashMap<>() {{
        put("world", argument -> player -> player.getWorld().getName().equals(argument));
        put("permission", argument -> player -> player.hasPermission(argument));
        put("gamemode", argument -> player -> player.getGameMode().name().equalsIgnoreCase(argument));
        put("environment", argument -> player -> player.getWorld().getEnvironment().name().equalsIgnoreCase(argument));
    }};
    public static final Pattern DYNAMIC = Pattern.compile("<(" + String.join("|", PlayerProcessor.placeholders.toArray(String[]::new)) + ")(|:.*)>");
    public static final Pattern ANIMATED = Pattern.compile("<(anim|animated)(:.*)+>");

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void loadConfig(JavaPlugin plugin) {
        var configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                configFile.createNewFile();
                try (var in = plugin.getResource("config.yml");
                     var out = Files.newOutputStream(configFile.toPath())) {
                    out.write(Objects.requireNonNull(in, "Invalid plugin jar file?").readAllBytes());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (var in = new FileReader(configFile, StandardCharsets.UTF_8)) {
            load(new Yaml().compose(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void load(Node config) {
        ANIMATED_LINE.clear();
        var animated = (MappingNode) ((MappingNode) config).getValue()
                .stream()
                .filter(n -> ((ScalarNode) n.getKeyNode()).getValue().equals("animated"))
                .findFirst()
                .orElseThrow()
                .getValueNode();
        //An animation line can be inside an animation line, we should share a line list everywhere
        animated.getValue().forEach(n -> ANIMATED_LINE.put(((ScalarNode) n.getKeyNode()).getValue(), new AnimatedLine(new ArrayList<>(), 1L)));
        animated.getValue().forEach(n -> {
            var line = ANIMATED_LINE.get(((ScalarNode) n.getKeyNode()).getValue());
            ((SequenceNode) n.getValueNode()).getValue()
                    .stream()
                    .map(ScalarNode.class::cast)
                    .map(ScalarNode::getValue)
                    .map(ScoreboardConfig::getLine)
                    .forEach(line.lines()::add);
        });
        var configs = (SequenceNode) ((MappingNode) config).getValue()
                .stream()
                .filter(n -> ((ScalarNode) n.getKeyNode()).getValue().equals("boards"))
                .findFirst()
                .orElseThrow()
                .getValueNode();
        ScoreboardConfig.CONFIGS.clear();
        ScoreboardConfig.CONFIGS.addAll(configs.getValue()
                .stream()
                .map(MappingNode.class::cast)
                .map(n -> {
                    var map = new HashMap<String, Node>();
                    n.getValue().forEach(n1 -> map.put(((ScalarNode) n1.getKeyNode()).getValue(), n1.getValueNode()));
                    return map;
                }).map(sConfig -> {
                    var title = ((ScalarNode) sConfig.get("title")).getValue();
                    var predicates = getContexts((SequenceNode) sConfig.get("contexts"));
                    var lines = ((SequenceNode) sConfig.get("lines")).getValue()
                            .stream()
                            .map(ScalarNode.class::cast)
                            .map(ScalarNode::getValue)
                            .map(ScoreboardConfig::getLine)
                            .toList();
                    Validate.isTrue(lines.size() < 16, "Too many lines!");
                    var period = (ScalarNode) sConfig.get("refreshPeriod");
                    return new ScoreboardConfig(
                            predicates,
                            lines,
                            getLine(title),
                            Integer.parseInt(period.getValue())
                    );
                })
                .toList()
        );
    }
    private static @NonNull List<@NonNull Predicate<@NonNull Player>> getContexts(SequenceNode raw) {
        var result = new ArrayList<@NonNull Predicate<@NonNull Player>>();
        for (var contextRaw: raw.getValue()) {
            var split = ((ScalarNode) contextRaw).getValue().split("=", 2);
            boolean negate = false;
            if (split[0].startsWith("!")) {
                split[0] = split[0].substring(1);
                negate = true;
            }
            Validate.isTrue(split.length == 2, "Couldn't parse context " + contextRaw);
            var context = Optional.ofNullable(providers.get(split[0]))
                    .map(o -> o.getContext(split[1]))
                    .orElseThrow();
            if (negate) context = context.negate();
            result.add(context);
        }
        return result;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ScoreboardLine getLine(@NonNull String s) {
        if (!DYNAMIC.matcher(s).find()) return new StaticLine(s);
        var animated = ANIMATED.matcher(s);

        if (animated.matches()) {
            var root = MiniMessage.miniMessage().deserializeToTree(s, TagResolver.builder()
                    .tag(Set.of("anim", "animated"), (q, ctx) -> Tag.selfClosingInserting(Component.empty()))
                    .build()
            );
            if (root.children().size() == 1 && root.children().getFirst() instanceof TagNode node) {
                var args = node.parts().stream().skip(1).map(TagPart::value).toList();
                var refreshRate = 1;
                try {
                    refreshRate = Integer.parseInt(args.getFirst());
                    args = args.subList(1, args.size());
                } catch (NumberFormatException ignored) {}
                if (args.size() == 1) {
                    var line = ANIMATED_LINE.get(args.getFirst());
                    Objects.requireNonNull(line, "Couldn't find animated line named " + args.get(0));
                    if (line.oncePerTimes() != refreshRate) line = new AnimatedLine(line.lines(), refreshRate);
                    return line;
                }
                if (args.size() > 1) {
                    return new AnimatedLine(args.stream().map(ScoreboardConfig::getLine).toList(), refreshRate);
                }
            }
        }
        return new DynamicLine(s);
    }

    public boolean contextMatch(Player player) {
        return contexts.stream().allMatch(p -> p.test(player));
    }

    @FunctionalInterface
    public interface ContextProvider {
        @NonNull Predicate<@NonNull Player> getContext(String argument);
    }
}
