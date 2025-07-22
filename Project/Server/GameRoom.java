// UCID: mramos2001
// Date: 07/21/2025
// Description: Extends Room to implement RPS logic with round lifecycle and player elimination

package Project.Server;

import Project.Common.Constants;
import Project.Common.User;
import java.util.*;

public class GameRoom extends Room {

    private List<ServerThread> activePlayers = new ArrayList<>();
    private Set<ServerThread> eliminatedPlayers = new HashSet<>();
    private Map<ServerThread, String> playerChoices = new HashMap<>();

    private int roundNumber = 1;
    private String currentPhase = "waiting";

    public GameRoom(String name) {
        super(name);
    }

    @Override
    protected synchronized void addClient(ServerThread client) {
        super.addClient(client);
        if (!activePlayers.contains(client)) {
            activePlayers.add(client);
            if (client.getUser() == null) {
                User user = new User();
                user.setClientId(client.getClientId());
                user.setClientName(client.getClientName());
                client.setUser(user);
            }
        }
        broadcastToActivePlayers(String.format("Player %s joined the game.", client.getClientName()));
    }

    @Override
    protected synchronized void removeClient(ServerThread client) {
        super.removeClient(client);
        activePlayers.remove(client);
        eliminatedPlayers.remove(client);
        playerChoices.remove(client);
        broadcastToActivePlayers(String.format("Player %s left the game.", client.getClientName()));
    }

    private void broadcastToActivePlayers(String message) {
        for (ServerThread client : activePlayers) {
            client.sendMessage(client.getClientId(), message);
        }
    }

    protected synchronized void onSessionStart() {
        roundNumber = 1;
        eliminatedPlayers.clear();
        broadcastToActivePlayers("Game started! Round 1 begins now.");
        onRoundStart();
    }

    protected synchronized void onRoundStart() {
        currentPhase = "choosing";
        playerChoices.clear();

        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player)) {
                playerChoices.put(player, null);
            }
        }

        broadcastToActivePlayers(String.format("Round %d started. Please submit your move!", roundNumber));
    }

    protected synchronized void onRoundEnd() {

        broadcastToActivePlayers(String.format("Round %d ended!", roundNumber));

        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player) && playerChoices.get(player) == null) {
                eliminatedPlayers.add(player);
                broadcastToActivePlayers(player.getClientName() + " did not make a move and is eliminated!");
            }
        }

        List<ServerThread> eligible = new ArrayList<>();
        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player)) {
                eligible.add(player);
            }
        }

        Set<ServerThread> roundLosers = new HashSet<>();

        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                ServerThread p1 = eligible.get(i);
                ServerThread p2 = eligible.get(j);

                String c1 = playerChoices.get(p1);
                String c2 = playerChoices.get(p2);

                if (c1 == null || c2 == null) continue;

                String result;
                if (c1.equals(c2)) {
                    result = "It's a tie!";
                } else if ((c1.equals("rock") && c2.equals("scissors")) ||
                           (c1.equals("scissors") && c2.equals("paper")) ||
                           (c1.equals("paper") && c2.equals("rock"))) {
                    p1.getUser().incrementPoints();
                    result = p1.getClientName() + " wins!";
                    roundLosers.add(p2);
                } else {
                    p2.getUser().incrementPoints();
                    result = p2.getClientName() + " wins!";
                    roundLosers.add(p1);
                }

                broadcastToActivePlayers(String.format("%s (%s) vs %s (%s) - %s",
                        p1.getClientName(), c1, p2.getClientName(), c2, result));
            }
        }

        for (ServerThread loser : roundLosers) {
            eliminatedPlayers.add(loser);
            broadcastToActivePlayers(loser.getClientName() + " is eliminated!");
        }

        List<ServerThread> remaining = new ArrayList<>();
        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player)) {
                remaining.add(player);
            }
        }

        if (remaining.size() == 1) {
            ServerThread winner = remaining.get(0);
            broadcastToActivePlayers("Game over! " + winner.getClientName() + " is the winner!");
            resetSession();
        } else if (remaining.isEmpty()) {
            broadcastToActivePlayers("Game over! No winners this round.");
            resetSession();
        } else {
            roundNumber++;
            broadcastToActivePlayers("Next round starting...");
            onRoundStart();
        }
    }

    protected synchronized void resetSession() {
        broadcastToActivePlayers("Resetting game session...");
        onSessionEnd();
    }

    protected synchronized void onSessionEnd() {
        broadcastToActivePlayers("Game over!");

        List<ServerThread> scoredPlayers = new ArrayList<>(activePlayers);
        scoredPlayers.sort((a, b) -> Integer.compare(b.getUser().getPoints(), a.getUser().getPoints()));

        StringBuilder scoreboard = new StringBuilder("\nFinal Scoreboard:\n");
        for (ServerThread player : scoredPlayers) {
            scoreboard.append(String.format("%s - %d points\n",
                    player.getClientName(),
                    player.getUser().getPoints()));
        }

        for (ServerThread player : activePlayers) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, scoreboard.toString());
        }

        for (ServerThread player : activePlayers) {
            player.getUser().reset();
            player.sendResetUserList();
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Game session ended. Awaiting next ready check.");
        }

        roundNumber = 1;
        eliminatedPlayers.clear();
        playerChoices.clear();
        currentPhase = "waiting";
    }

    @Override
    public synchronized void handleChoice(ServerThread player, String choice) {
        if (!"choosing".equals(currentPhase)) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You can't pick right now.");
            return;
        }

        if (!isValidChoice(choice)) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid choice. Use rock, paper, or scissors.");
            return;
        }

        if (!playerChoices.containsKey(player)) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You're not part of this round.");
            return;
        }

        if (playerChoices.get(player) != null) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You already picked.");
            return;
        }

        playerChoices.put(player, choice.toLowerCase());
        broadcastToActivePlayers(player.getClientName() + " has picked.");


        if (allChoicesSubmitted()) {
            onRoundEnd();
        }
    }

    private boolean allChoicesSubmitted() {
        for (Map.Entry<ServerThread, String> entry : playerChoices.entrySet()) {
            if (entry.getValue() == null) return false;
        }
        return true;
    }

    private int countSubmittedChoices() {
        int count = 0;
        for (String val : playerChoices.values()) {
            if (val != null) count++;
        }
        return count;
    }

    private boolean isValidChoice(String choice) {
        return choice.equalsIgnoreCase("rock") ||
               choice.equalsIgnoreCase("paper") ||
               choice.equalsIgnoreCase("scissors");
    }

    private void sendRoundResults(Map<ServerThread, Integer> roundPoints) {
        for (Map.Entry<ServerThread, Integer> entry : roundPoints.entrySet()) {
            ServerThread player = entry.getKey();
            int pointsEarned = entry.getValue();
            String summary = "You earned " + pointsEarned + " point" + (pointsEarned != 1 ? "s." : ".");
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, summary);
        }
    }
}