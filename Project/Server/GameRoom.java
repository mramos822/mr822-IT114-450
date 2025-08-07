// UCID: mramos2001
// Date: 07/21/2025
// Description: Extends Room to implement RPS logic with round lifecycle and player elimination

package Project.Server;

import Project.Common.*;
import java.util.*;


public class GameRoom extends Room {

    private List<ServerThread> activePlayers = new ArrayList<>();
    private Set<ServerThread> eliminatedPlayers = new HashSet<>();
    private Map<ServerThread, String> playerChoices = new HashMap<>();
    private boolean postGameCountdownStarted = false;
    private boolean gameStarted = false;
    private Thread postGameCountdownThread;
    private String gameMode = "Classic Mode";

    private int roundNumber = 1;
    private String currentPhase = "waiting";
    private volatile boolean gameOver = false;
    private int currentRoundTime = 15;
    private final int MIN_ROUND_TIME = 3;
    private Timer roundTimer;
    private boolean choiceCooldownEnabled = false;
    private boolean gameInProgress = false;


    public GameRoom(String name) {
        super(name);
    }

@Override
protected synchronized void addClient(ServerThread client) {
    super.addClient(client);

    if (client.getUser() == null) {
        User user = new User();
        user.setClientId(client.getClientId());
        user.setClientName(client.getClientName());
        client.setUser(user);
    }

    if (gameStarted && !gameOver) {
        client.getUser().setSpectator(true);
        spectators.add(client);
        broadcastSystemMessage(client.getClientName() + " joined as a spectator.");
    } else {
        if (!activePlayers.contains(client)) {
            activePlayers.add(client);
        }
        broadcastToActivePlayers(String.format("Player %s joined the game.", client.getClientName()));
    }
}


    @Override
    protected synchronized void removeClient(ServerThread client) {
        super.removeClient(client);
        activePlayers.remove(client);
        eliminatedPlayers.remove(client);
        playerChoices.remove(client);
        broadcastToActivePlayers(String.format("Player %s left the game.", client.getClientName()));

        Payload remove = new Payload();
        remove.setPayloadType(Project.Common.PayloadType.READY_STATUS_REMOVE);
        remove.setClientId(client.getClientId());

        for (ServerThread player : activePlayers) {
            player.sendToClient(remove);
        }

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if (activePlayers.size() == 1) {
                        ServerThread lonePlayer = activePlayers.get(0);

                        Payload notify = new Payload();
                        notify.setPayloadType(PayloadType.ROOM_CLOSING);
                        notify.setMessage("You're the only one left in the room. Click below to return to the lobby.");

                        lonePlayer.sendToClient(notify);
                    }
                }
            }
        }, 10);

        checkReadinessAndStartIfReady();
    }

    private void broadcastToActivePlayers(String message) {
        for (ServerThread client : activePlayers) {
            client.sendMessage(client.getClientId(), message);
        }
    }

    protected synchronized void onSessionStart() {
        setGameInProgress(true);
        roundNumber = 1;
        currentRoundTime = 15;
        eliminatedPlayers.clear();
        playerChoices.clear();
        postGameCountdownStarted = false;
        postGameCountdownThread = null;

        activePlayers.clear();
        for (ServerThread st : getClientsInRoom()) {
            activePlayers.add(st);
            if (st.getUser() != null) {
                st.getUser().resetPoints();
            }
        }
        for (ServerThread spectator : getSpectators()) {
            if (!activePlayers.contains(spectator)) {
                activePlayers.add(spectator);
            }
        }

        sendUpdatedPointsToAll(new ArrayList<>());

        for (ServerThread player : activePlayers) {
            if (player.getUser() != null && !player.getUser().isAway()) {
                Payload start = new Payload();
                start.setPayloadType(Project.Common.PayloadType.START);
                start.setMessage(String.valueOf(currentRoundTime));
                player.sendToClient(start);
            }
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    onRoundStart();
                }
            }
        }, 30);
    }

    protected synchronized void onRoundStart() {
        setGameInProgress(true);
        currentPhase = "choosing";
        playerChoices.clear();

        List<ServerThread> nonAwayPlayers = new ArrayList<>();
        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player) 
                && !player.getUser().isAway()) {

                if (!player.getUser().isSpectator()) {
                    playerChoices.put(player, null);
                }
                nonAwayPlayers.add(player);
            }
        }

        for (ServerThread player : nonAwayPlayers) {
            sendMessageToPlayer(player, "Game started! Get ready for Round " + roundNumber + "...");
            sendMessageToPlayer(player, "Available moves: " + String.join(", ", getAvailableMoves()));

            if (roundNumber == 1) {
                if (allowChoiceChanges) {
                    sendMessageToPlayer(player, "Cooldown is ON. You can change your pick until time runs out.");
                } else {
                    sendMessageToPlayer(player, "Cooldown is OFF. Your first choice is final.");
                }
            }

            sendMessageToPlayer(player, "Round " + roundNumber + " started. Please submit your move!");
        }

        for (ServerThread player : activePlayers) {
            if (player.getUser() != null && !player.getUser().isAway()) {
                Payload start = new Payload();
                start.setPayloadType(Project.Common.PayloadType.START);
                start.setMessage(String.valueOf(currentRoundTime));
                player.sendToClient(start);
            }
        }

        sendUpdatedPointsToAll(new ArrayList<>());

        if (roundTimer != null) {
            roundTimer.cancel();
        }

        roundTimer = new Timer();
        roundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if ("choosing".equals(currentPhase)) {
                        onRoundEnd();
                    }
                }
            }
        }, currentRoundTime * 1000);
    }

    private List<ServerThread> getSpectators() {
        List<ServerThread> spectators = new ArrayList<>();
        for (ServerThread client : getClientsInRoom()) {
            if (client.getUser() != null && client.getUser().isSpectator()) {
                spectators.add(client);
            }
        }
        return spectators;
    }


    private void sendMessageToPlayer(ServerThread player, String message) {
        Payload p = new Payload();
        p.setPayloadType(Project.Common.PayloadType.MESSAGE);
        p.setMessage(message);
        player.sendToClient(p);
    }


    protected synchronized void onRoundEnd() {
        setGameInProgress(false);

        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        List<String> battleMessages = new ArrayList<>();
        broadcastToActivePlayers(String.format("Round %d ended!", roundNumber));

        for (ServerThread player : activePlayers) {
            if (player.getUser().isAway() || player.getUser().isSpectator()) continue;

            if (!eliminatedPlayers.contains(player) && playerChoices.get(player) == null) {
                eliminatedPlayers.add(player);
                broadcastToActivePlayers(player.getClientName() + " did not make a move and is eliminated!");
            }
        }

        List<ServerThread> eligible = new ArrayList<>();
        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player)
                && !player.getUser().isAway()
                && !player.getUser().isSpectator()) {
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

                int resultCode = compareMoves(c1.toLowerCase(), c2.toLowerCase());
                String result;

                if (resultCode == 0) {
                    p1.getUser().addPoints(1);
                    p2.getUser().addPoints(1);
                    result = "It's a tie! Both get 1 point.";
                } else if (resultCode > 0) {
                    p1.getUser().addPoints(3);
                    result = p1.getClientName() + " wins and gets 3 points!";
                    roundLosers.add(p2);
                } else {
                    p2.getUser().addPoints(3);
                    result = p2.getClientName() + " wins and gets 3 points!";
                    roundLosers.add(p1);
                }

                String battleMsg = String.format("%s (%s) vs %s (%s) - %s",
                        p1.getClientName(), c1, p2.getClientName(), c2, result);

                broadcastToActivePlayers(battleMsg);
                battleMessages.add(battleMsg);
            }
        }

        for (ServerThread loser : roundLosers) {
            if (!eliminatedPlayers.contains(loser)) {
                eliminatedPlayers.add(loser);
                broadcastToActivePlayers(loser.getClientName() + " is eliminated!");
            }
        }

        List<ServerThread> remaining = new ArrayList<>();
        for (ServerThread player : activePlayers) {
            if (!eliminatedPlayers.contains(player) && !player.getUser().isSpectator()) {
                remaining.add(player);
            }
        }

        if (remaining.size() == 1) {
            ServerThread winner = remaining.get(0);
            broadcastToActivePlayers("Game over! " + winner.getClientName() + " is the winner!");
            sendUpdatedPointsToAll(battleMessages);
            onSessionEnd();
        } else if (remaining.isEmpty()) {
            broadcastToActivePlayers("Game over! No winners this round.");
            sendUpdatedPointsToAll(battleMessages);
            onSessionEnd();
        } else {
            if (gameMode.equalsIgnoreCase("Speed Drop Mode")) {
                if (currentRoundTime > MIN_ROUND_TIME) {
                    currentRoundTime--;
                }
            }
            roundNumber++;
            broadcastToActivePlayers("Next round starting...");

            sendUpdatedPointsToAll(battleMessages);
            sendStartPayloadToAll();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (GameRoom.this) {
                        onRoundStart();
                    }
                }
            }, 10);
        }
    }


    protected synchronized void resetSession() {
        broadcastToActivePlayers("Resetting game session...");
        onSessionEnd();
    }

    protected synchronized void onSessionEnd() {
        setGameInProgress(false);
        System.out.println("[DEBUG] isGameInProgress = " + isGameInProgress()); 
        gameOver = true;

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
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Game session ended. Awaiting next ready check.");
        }

        roundNumber = 1;
        gameStarted = false;
        eliminatedPlayers.clear();
        playerChoices.clear();
        currentPhase = "waiting";

        for (ServerThread player : activePlayers) {
            player.setReady(false);
            broadcastReadyStatus(player.getClientId(), false);
        }

        postGameCountdownStarted = true;
        startPostGameCountdown();

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if (activePlayers.size() == 1) {
                        ServerThread lonePlayer = activePlayers.get(0);

                        Payload notify = new Payload();
                        notify.setPayloadType(PayloadType.ROOM_CLOSING);
                        notify.setMessage("You're the only one left in the room. Click below to return to the lobby.");

                        lonePlayer.sendToClient(notify);
                    }
                }
            }
        }, 50);
    }

    // UCID: mramos2001
    // Date: 08/05/2025
    // Description: Processes the player's choice and informs other players
    @Override
    public synchronized void handleChoice(ServerThread player, String choice) {
        if (!"choosing".equals(currentPhase)) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You can't pick right now.");
            return;
        }
        if (player.getUser().isAway()) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You are marked as AWAY and cannot participate.");
            return;
        }
        if (player.getUser().isSpectator()) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You are a spectator and cannot participate.");
            return;
        }

        List<String> allowedMoves = getAvailableMoves();
        if (!allowedMoves.contains(choice.toUpperCase())) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID,
                "Invalid choice for current mode. Allowed moves: " + String.join(", ", allowedMoves));
            return;
        }

        if (!playerChoices.containsKey(player)) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You're not part of this round.");
            return;
        }

        String existingChoice = playerChoices.get(player);
        if (existingChoice != null && !choiceCooldownEnabled) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Cooldown is OFF. Your first choice is final.");
            return;
        }

        playerChoices.put(player, choice.toLowerCase());
        broadcastToActivePlayers(player.getClientName() + " has picked.");
        sendUpdatedPointsToAll(new ArrayList<>());

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

    private boolean isValidChoice(String choice) {
        return choice.equalsIgnoreCase("rock") ||
            choice.equalsIgnoreCase("paper") ||
            choice.equalsIgnoreCase("scissors") ||
            choice.equalsIgnoreCase("lizard") ||
            choice.equalsIgnoreCase("spock");
    }


    // UCID: mramos2001
    // Date: 2025-08-06
    // Description: Handles READY payloads and spectator toggles without using getLastReadyMessage()
    // UCID: mramos2001
    // Date: 2025-08-06
    // Description: Server announces that a client joined as spectator by broadcasting a message.
    public void handleReady(ServerThread sender, boolean isReady, boolean isSpectator) {
        synchronized (this) {
            if (isSpectator) {
            sender.getUser().setSpectator(true);
            sender.setReady(false);
            broadcastSystemMessage(sender.getClientName() + " has joined as a spectator.");
            sendUpdatedPointsToAll(new ArrayList<>());

            Payload spectatorPayload = new Payload();
            spectatorPayload.setPayloadType(PayloadType.READY_STATUS);
            spectatorPayload.setClientId(sender.getClientId());
            spectatorPayload.setMessage("SPECTATOR");
            for (ServerThread player : activePlayers) {
                player.sendToClient(spectatorPayload);
            }

            return;
        }

            sender.setReady(isReady);
            sender.getUser().setSpectator(false); 

            broadcastReadyStatus(sender.getClientId(), isReady);
            broadcastToActivePlayers(sender.getClientName() + " is " + (isReady ? "READY" : "NOT READY") + ".");

            long readyCount = activePlayers.stream()
                    .filter(p -> !p.getUser().isAway() && !p.getUser().isSpectator())
                    .filter(ServerThread::isReady)
                    .count();

            long totalEligible = activePlayers.stream()
                    .filter(p -> !p.getUser().isAway() && !p.getUser().isSpectator())
                    .count();

            if (!gameStarted && readyCount == totalEligible && totalEligible > 1) {
                gameStarted = true;
                broadcastToActivePlayers("All non-away and non-spectator players are ready. Starting game...");
                postGameCountdownStarted = false;
                onSessionStart();
            } else if (!postGameCountdownStarted && !gameStarted) {
                postGameCountdownStarted = true;
                startPostGameCountdown();
            }
        }
    }

    public void sendUpdatedPointsToAll(List<String> battleMessages) {
        PointsPayload pointsPayload = new PointsPayload();

        for (ServerThread player : activePlayers) {
            UserStatus status = new UserStatus(
                player.getClientName(),
                player.getClientId(),
                player.getUser().getPoints()
            );
            status.setAway(player.getUser().isAway());
            status.setSpectator(player.getUser().isSpectator());
            pointsPayload.addUserStatus(status);
        }

        Set<Long> eliminatedIds = new HashSet<>();
        for (ServerThread p : eliminatedPlayers) {
            eliminatedIds.add(p.getClientId());
        }
        pointsPayload.setEliminatedIds(eliminatedIds);

        Set<Long> pendingIds = new HashSet<>();
        if ("choosing".equals(currentPhase)) {
            for (Map.Entry<ServerThread, String> entry : playerChoices.entrySet()) {
                if (entry.getValue() == null && !entry.getKey().getUser().isAway()) {
                    pendingIds.add(entry.getKey().getClientId());
                }
            }
        }
        pointsPayload.setPendingPickIds(pendingIds);

        for (String msg : battleMessages) {
            pointsPayload.addBattleMessage(msg);
        }

        for (ServerThread player : activePlayers) {
            player.sendToClient(pointsPayload);
        }
    }


    // UCID: mramos2001
    // Date: 2025-08-06
    // Description: Post-game countdown, marking unready players as spectators and updating UI instantly
    private void startPostGameCountdown() {
        setGameInProgress(false);
        if (postGameCountdownStarted) return;
        postGameCountdownStarted = true;

        if (postGameCountdownThread != null && postGameCountdownThread.isAlive()) {
            postGameCountdownThread.interrupt();
        }

        postGameCountdownThread = new Thread(() -> {
            int timeLeft = 15;
            while (timeLeft > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    postGameCountdownThread = null;
                    return;
                }

                String tickMessage = "Play again? Time left: " + timeLeft;
                for (ServerThread player : activePlayers) {
                    player.sendCountdownTick(tickMessage);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    postGameCountdownThread = null;
                    return;
                }

                timeLeft--;
            }

            if (gameOver) {
                List<ServerThread> readyPlayers = new ArrayList<>();
                List<ServerThread> unreadyPlayers = new ArrayList<>();

                for (ServerThread player : activePlayers) {
                    if (player.getUser().isAway()) {
                        continue;
                    }
                    if (player.isReady()) {
                        readyPlayers.add(player);
                    } else {
                        unreadyPlayers.add(player);
                    }
                }

                for (ServerThread player : unreadyPlayers) {
                    player.getUser().setSpectator(true);
                    player.setReady(false);
                    broadcastSystemMessage(player.getClientName() + " has been set to Spectator for the next game.");
                    player.sendMessage(Constants.DEFAULT_CLIENT_ID,
                            "You were not ready. You are now a spectator for the next game.");
                }

                sendUpdatedPointsToAll(new ArrayList<>());

                if (readyPlayers.size() >= 2) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        postGameCountdownThread = null;
                        return;
                    }

                    synchronized (this) {
                        activePlayers.clear();
                        activePlayers.addAll(getClientsInRoom());

                        gameStarted = true;
                        gameOver = false;
                        postGameCountdownStarted = false;
                        postGameCountdownThread = null;
                        onSessionStart();
                    }
                } else {
                    for (ServerThread player : readyPlayers) {
                        player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Not enough players to restart. Returning to lobby.");
                        player.sendKickToLobby();
                    }
                    postGameCountdownThread = null;
                }
            }
        });

        postGameCountdownThread.start();
    }


    private void sendStartPayloadToAll() {
        for (ServerThread player : activePlayers) {
            Payload start = new Payload();
            start.setPayloadType(Project.Common.PayloadType.START);
            start.setMessage(String.valueOf(currentRoundTime));
            player.sendToClient(start);
        }
    }

    private int compareMoves(String move1, String move2) {
        if (move1.equals(move2)) return 0;

        return switch (move1) {
            case "rock" -> (move2.equals("scissors") || move2.equals("lizard")) ? 1 : -1;
            case "paper" -> (move2.equals("rock") || move2.equals("spock")) ? 1 : -1;
            case "scissors" -> (move2.equals("paper") || move2.equals("lizard")) ? 1 : -1;
            case "lizard" -> (move2.equals("spock") || move2.equals("paper")) ? 1 : -1;
            case "spock" -> (move2.equals("scissors") || move2.equals("rock")) ? 1 : -1;
            default -> 0;
        };
    }

    // UCID: mramos2001
    // Date: 2025-08-05
    // Description: Prevents client from picking again within 2 seconds if cooldown is enabled

    public void setGameMode(String mode, boolean choiceCooldown, String setBy) {
        if (mode != null) {
            this.gameMode = mode;
            this.choiceCooldownEnabled = choiceCooldown;

            this.allowChoiceChanges = choiceCooldown;

            broadcastToActivePlayers("Game mode set to: " + mode + " by " + setBy);
            broadcastToActivePlayers("Choice cooldown is " + (choiceCooldown ? "ENABLED (you may change your pick)" : "DISABLED (first choice is final)"));

            GameSettingsPayload settings = new GameSettingsPayload();
            settings.setGameMode(mode);
            settings.setChoiceCooldown(choiceCooldown);
            for (ServerThread player : activePlayers) {
                player.sendToClient(settings);
            }
        }
    }


    private List<String> getAvailableMoves() {
        switch (gameMode.toUpperCase()) {
            case "RPS5 - ALWAYS":
                return Arrays.asList("ROCK", "PAPER", "SCISSORS", "LIZARD", "SPOCK");
            case "RPS5 - AT 3 PLAYERS":
                if (activePlayers.size() <= 3) {
                    return Arrays.asList("ROCK", "PAPER", "SCISSORS", "LIZARD", "SPOCK");
                } else {
                    return Arrays.asList("ROCK", "PAPER", "SCISSORS");
                }
            case "SPEED DROP MODE":
            case "CLASSIC MODE":
            default:
                return Arrays.asList("ROCK", "PAPER", "SCISSORS");
        }
    }

    public List<ServerThread> getActivePlayers() {
        return activePlayers;
    }

    private boolean allowChoiceChanges = false;

    public void setAllowChoiceChanges(boolean allowChoiceChanges) {
        this.allowChoiceChanges = allowChoiceChanges;
    }

    @Override
    public void joinRoom(ServerThread client) {
        super.joinRoom(client);

        if (gameStarted && !gameOver) {
            client.getUser().setSpectator(true);
            spectators.add(client);
            broadcastSystemMessage(client.getClientName() + " joined as a spectator.");

            Payload spectatorPayload = new Payload();
            spectatorPayload.setPayloadType(PayloadType.SPECTATOR);
            client.sendToClient(spectatorPayload);
        } else {
            if (!activePlayers.contains(client)) {
                activePlayers.add(client);
            }
            broadcastToActivePlayers("Player " + client.getClientName() + " joined the game.");
        }

        ExtendedGameSettingsPayload payload = new ExtendedGameSettingsPayload();
        payload.setGameMode(this.gameMode);
        payload.setChoiceCooldown(this.choiceCooldownEnabled);
        payload.setAllowChoiceChanges(this.allowChoiceChanges);
        payload.setGameStarted(this.gameStarted);
        client.sendToClient(payload);
    }

    public GameSettingsPayload getCurrentGameSettingsPayload() {
        GameSettingsPayload payload = new GameSettingsPayload();
        payload.setGameMode(this.gameMode);
        payload.setAllowChoiceChanges(this.allowChoiceChanges);
        payload.setChoiceCooldown(this.choiceCooldownEnabled);
        return payload;
    }

    public String getGameMode() {
        return gameMode;
    }

    public boolean isChoiceCooldown() {
        return choiceCooldownEnabled;
    }

    public boolean isAllowChoiceChanges() {
        return allowChoiceChanges;
    }

    private synchronized void checkReadinessAndStartIfReady() {
        long readyCount = activePlayers.stream()
                .filter(p -> !p.getUser().isAway() && !p.getUser().isSpectator())
                .filter(ServerThread::isReady)
                .count();

        long totalNonAway = activePlayers.stream()
                .filter(p -> !p.getUser().isAway())
                .count();

        if (!gameStarted && readyCount == totalNonAway && totalNonAway > 1) {
            gameStarted = true;
            broadcastToActivePlayers("All non-away players are ready. Starting game...");
            postGameCountdownStarted = false;
            onSessionStart();
        }
    }

    // UCID: mramos2001
    // Date: 08/05/2025
    // Description: Handles toggling of away status and triggers re-check of readiness
    public void handleAwayStatusChange(ServerThread player, boolean isAway) {
        if (player.getUser().isSpectator()) {
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Spectators cannot toggle away status.");
            return;
        }

        player.getUser().setAway(isAway);

        String message = player.getClientName() + (isAway ? " is now AWAY." : " is no longer AWAY.");
        broadcastToActivePlayers(message);

        sendUpdatedPointsToAll(new ArrayList<>());

        checkNonAwayReadinessAndStartIfReady();
    }

    private synchronized void checkNonAwayReadinessAndStartIfReady() {
        List<ServerThread> eligiblePlayers = new ArrayList<>();
        for (ServerThread player : activePlayers) {
            if (!player.getUser().isAway() && !player.getUser().isSpectator()) {
                eligiblePlayers.add(player);
            }
        }

        long readyCount = eligiblePlayers.stream()
            .filter(ServerThread::isReady)
            .count();
        int total = eligiblePlayers.size();

        if (!gameStarted && readyCount == total && total > 1) {
            gameStarted = true;
            broadcastToActivePlayers("All non-away and non-spectator players are ready. Starting game...");
            postGameCountdownStarted = false;
            onSessionStart();
        }
    }

    private Set<ServerThread> spectators = new HashSet<>();
    public void addSpectator(ServerThread player) {
        spectators.add(player);
    }

    // UCID: mramos2001
    // Date: 2025-08-06
    // Description: Sends a system message to all players in the room
    public void broadcastSystemMessage(String msg) {
        relay(null, "[System]: " + msg);
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getCurrentRoundTime() {
        return currentRoundTime;
    }

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    public void setGameInProgress(boolean inProgress) {
        this.gameInProgress = inProgress;
    }

}
