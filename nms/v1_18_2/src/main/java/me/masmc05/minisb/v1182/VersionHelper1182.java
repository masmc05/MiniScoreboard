package me.masmc05.minisb.v1182;

import io.papermc.paper.adventure.PaperAdventure;
import me.masmc05.minisb.PlayerBoard;
import me.masmc05.minisb.VersionHelp;
import net.kyori.adventure.text.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_18_R2.CraftServer;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.minecraft.network.protocol.game.ClientboundSetObjectivePacket.*;
import static net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket;

public enum VersionHelper1182 implements VersionHelp {
    ISTANCE;
    public boolean checkCompatible() {
        try {
            return Bukkit.getServer() instanceof CraftServer;
        } catch (Throwable t) {
            return false;
        }
    }

    public PlayerBoard getBoard(Player player, int lines) {
        return new Board(player, lines);
    }

    private record Board(Player player, Objective objective, PlayerTeam[] teams) implements PlayerBoard {
        private static final String[] names = IntStream.range(0, 15).mapToObj(Integer::toHexString).map("§"::concat).toArray(String[]::new);

        private static final String[] indexes = IntStream.range(0, 15).mapToObj(Integer::toHexString).toArray(String[]::new); //Geyser compatibility
        Board(Player player, int lines) {
            this(player, new Objective(new Scoreboard(),
                            "mini_board",
                            ObjectiveCriteria.DUMMY,
                            TextComponent.EMPTY,
                            ObjectiveCriteria.RenderType.INTEGER
                    ), new PlayerTeam[lines]);
            send(new ClientboundSetObjectivePacket(objective, METHOD_ADD));
            send(new ClientboundSetDisplayObjectivePacket(1, objective));
            for (int i = 0; i < lines; i++) {
                var team = teams[i] = new PlayerTeam(objective.getScoreboard(), indexes[i]);
                team.setDisplayName(TextComponent.EMPTY);
                team.getPlayers().add(names[i]);
                send(createAddOrModifyPacket(team, true));
                send(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, objective.getName(), names[i], lines - i));
            }
        }

        @Override
        public void title(@NonNull Component displayName) {
            objective.displayName = PaperAdventure.asVanilla(displayName);
            send(new ClientboundSetObjectivePacket(objective, METHOD_CHANGE));
        }

        @Override
        public void setLine(int line, @NotNull Component component) {
            var team = teams[line];
            team.setPlayerPrefix(PaperAdventure.asVanilla(component));
            send(createAddOrModifyPacket(team, false));
        }

        @Override
        public void clear() {
            send(new ClientboundSetObjectivePacket(objective, METHOD_REMOVE));
            Stream.of(teams).map(ClientboundSetPlayerTeamPacket::createRemovePacket).forEach(this::send);
        }

        private void send(Packet<?> packet) {
            Optional.of(player())
                    .map(CraftPlayer.class::cast)
                    .map(CraftPlayer::getHandle)
                    .map(p -> p.connection)
                    .ifPresent(c -> c.send(packet));
        }
    }

}