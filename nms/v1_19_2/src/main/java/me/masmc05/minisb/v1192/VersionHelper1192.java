package me.masmc05.minisb.v1192;

import io.papermc.paper.adventure.PaperAdventure;
import me.masmc05.minisb.PlayerBoard;
import me.masmc05.minisb.VersionHelp;
import net.kyori.adventure.text.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.minecraft.network.protocol.game.ClientboundSetObjectivePacket.*;
import static net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket;
import static net.minecraft.server.ServerScoreboard.Method.CHANGE;

public enum VersionHelper1192 implements VersionHelp {
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
                            CommonComponents.EMPTY,
                            ObjectiveCriteria.RenderType.INTEGER
                    ), new PlayerTeam[lines]);
            send(new ClientboundSetObjectivePacket(objective, METHOD_ADD));
            send(new ClientboundSetDisplayObjectivePacket(1, objective));
            for (int i = 0; i < lines; i++) {
                if (teams[i] != null) continue;
                var team = teams[i] = new PlayerTeam(objective.getScoreboard(), indexes[i]);
                team.getPlayers().add(names[i]);
                send(createAddOrModifyPacket(team, true));
                send(new ClientboundSetScorePacket(CHANGE, objective.getName(), names[i], lines - i));
            }
        }

        @Override
        public void title(@NotNull Component displayName) {
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
            ((CraftPlayer) player()).getHandle().connection.send(packet);
        }
    }

}