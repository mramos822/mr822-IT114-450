package Project.Client;

import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.User;
import Project.Common.UserStatus;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class GameAreaUI extends JFrame {
    public static GameAreaUI instance;

    private UserListPanel userListPanel;
    private JButton rockButton, paperButton, scissorsButton;
    private JButton lizardButton, spockButton;
    private JLabel timerLabel;
    private Timer countdownTimer;
    private int timeLeft = 10;
    private boolean hasPicked = false;
    private JLabel gameOverLabel;
    private boolean countdownRunning = false;
    private int postGameTimeLeft = 15;
    private GameEventsPanel gameEventsPanel;
    private String currentGameMode = "Classic Mode";

    private JPanel postGamePanel;
    private JLabel postGameCountdownLabel;
    private JButton playAgainButton;
    private JButton returnToLobbyButton;
    private JLabel statusLabel;
    
    private JPanel readyStatusPanel;
    private DefaultListModel<String> readyStatusListModel;
    private JList<String> readyStatusList;
    private final ConcurrentHashMap<Long, Boolean> readyStatusMap = new ConcurrentHashMap<>();
    private Timer postGameCountdownTimer;
    private boolean choiceCooldownEnabled = false;
    private boolean hasLoggedCooldownStatus = false;
    private boolean roundActive = false;
    private boolean isFirstRoundOfGame = true;
    private JButton awayToggleButton;
    public boolean isAway = false;
    private JPanel spectatorPanel;
    private JButton spectatorReturnButton;
    private JPanel gameViewPanel;

public GameAreaUI(Point location) {
    setTitle("RPS - Game Area");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(800, 500);

    if (location != null) {
        setLocation(location);
    } else {
        setLocationRelativeTo(null);
    }

    setLayout(new BorderLayout());
    instance = this;

    // ---------- Game View Panel ----------
    gameViewPanel = new JPanel(new BorderLayout());
    gameViewPanel.setBorder(BorderFactory.createTitledBorder("Game View"));

    timerLabel = new JLabel("Time left: 15", SwingConstants.CENTER);
    timerLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
    gameViewPanel.add(timerLabel, BorderLayout.NORTH);

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

    rockButton = new JButton("Rock");
    paperButton = new JButton("Paper");
    scissorsButton = new JButton("Scissors");
    lizardButton = new JButton("Lizard");
    spockButton = new JButton("Spock");
    lizardButton.addActionListener(this::handlePick);
    spockButton.addActionListener(this::handlePick);
    rockButton.addActionListener(this::handlePick);
    paperButton.addActionListener(this::handlePick);
    scissorsButton.addActionListener(this::handlePick);

    buttonPanel.add(rockButton);
    buttonPanel.add(paperButton);
    buttonPanel.add(scissorsButton);
    buttonPanel.add(lizardButton);
    buttonPanel.add(spockButton);

    awayToggleButton = new JButton("Mark Away");
    awayToggleButton.addActionListener(e -> {
        if (!isAway && !postGamePanel.isVisible() && !isEliminated()) {
            logEvent("You can only mark yourself away after a game ends.");
            return;
        }

        isAway = !isAway;
        awayToggleButton.setText(isAway ? "Return" : "Mark Away");

        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.AWAY_STATUS);
        payload.setMessage(String.valueOf(isAway));
        Client.INSTANCE.sendPayload(payload);

        if (isAway) {
            stopPostGameCountdown();
            playAgainButton.setEnabled(false);
            returnToLobbyButton.setEnabled(false);
            postGameCountdownLabel.setText("You are marked as away. Waiting for next game...");
            logEvent("You're away and won't rejoin until next round starts.");
        } else if (postGamePanel.isVisible()) {
            awayToggleButton.setEnabled(false);
        }

        updateAwayButtonVisibility();
    });

    buttonPanel.add(awayToggleButton);

    lizardButton.setVisible(false);
    spockButton.setVisible(false);

    gameViewPanel.add(buttonPanel, BorderLayout.CENTER);

    // ---------- Spectator Panel ----------
    initSpectatorPanel();

    // ---------- Player List Panel ----------
    userListPanel = new UserListPanel();
    userListPanel.setBorder(BorderFactory.createTitledBorder("Players"));

    // ---------- Top Row Panel ----------
    JPanel topRowPanel = new JPanel(new GridLayout(1, 2));
    topRowPanel.add(gameViewPanel);
    topRowPanel.add(userListPanel);

    // ---------- Game Events Panel ----------
    gameEventsPanel = new GameEventsPanel();
    gameEventsPanel.setBorder(BorderFactory.createTitledBorder("Game Events"));

    // ---------- Split Pane ----------
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topRowPanel, gameEventsPanel);
    splitPane.setResizeWeight(0.5);
    splitPane.setDividerSize(4);
    splitPane.setOneTouchExpandable(true);
    add(splitPane, BorderLayout.CENTER);

    // ---------- Game Over Label ----------
    gameOverLabel = new JLabel("Game Over! Awaiting next ready check.", SwingConstants.CENTER);
    gameOverLabel.setForeground(Color.RED);
    gameOverLabel.setVisible(false);
    add(gameOverLabel, BorderLayout.SOUTH);

    // ---------- Status Label ----------
    statusLabel = new JLabel("Status: Playing", SwingConstants.CENTER);
    statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
    statusLabel.setForeground(Color.DARK_GRAY);
    add(statusLabel, BorderLayout.PAGE_END);

    // ---------- Post-game Panel ----------
    postGamePanel = new JPanel(new FlowLayout());
    postGameCountdownLabel = new JLabel("Play again? Time left: 15");
    playAgainButton = new JButton("Play Again");
    returnToLobbyButton = new JButton("Return to Lobby");

    postGamePanel.add(postGameCountdownLabel);
    postGamePanel.add(playAgainButton);
    postGamePanel.add(returnToLobbyButton);
    postGamePanel.setVisible(false);
    add(postGamePanel, BorderLayout.PAGE_START);

    // ---------- Ready Status Panel ----------
    readyStatusListModel = new DefaultListModel<>();
    readyStatusList = new JList<>(readyStatusListModel);
    readyStatusPanel = new JPanel(new BorderLayout());
    readyStatusPanel.setBorder(BorderFactory.createTitledBorder("Players Ready Status"));
    readyStatusPanel.add(new JScrollPane(readyStatusList), BorderLayout.CENTER);
    readyStatusPanel.setVisible(false);
    add(readyStatusPanel, BorderLayout.WEST);

    readyStatusList.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String val = value.toString();

            if (val.endsWith("AWAY")) {
                c.setForeground(Color.GRAY);
            } else if (val.endsWith("READY")) {
                c.setForeground(new Color(0, 128, 0)); // green
            } else {
                c.setForeground(Color.RED); // not ready
            }

            return c;
        }
    });

    // ---------- Button actions ----------
    playAgainButton.addActionListener(e -> handlePlayAgain());
    returnToLobbyButton.addActionListener(e -> handleReturnToLobby());
}


    public GameAreaUI() {
        this(null);
    }

    public static GameAreaUI getInstance(Point location) {
        if (instance == null) {
            instance = new GameAreaUI(location);
        }
        return instance;
    }

    public static GameAreaUI getInstanceIfExists() {
        return instance;
    }

    public static GameAreaUI getInstance() {
        if (instance == null) {
            instance = new GameAreaUI();
        }
        return instance;
    }

    public static void updateUserPanel(List<UserStatus> statuses, Set<Long> eliminatedIds, Set<Long> pendingIds) {
        if (instance != null && instance.userListPanel != null) {
            instance.userListPanel.updateUserList(statuses, eliminatedIds, pendingIds);

            Long clientId = Client.INSTANCE.getClientId();
            boolean isEliminated = eliminatedIds.contains(clientId);
            boolean isSpectator = statuses.stream()
                    .anyMatch(s -> s.getId() == clientId && s.isSpectator());
            boolean isPending = pendingIds.contains(clientId);

            instance.setTitle((isEliminated || isSpectator) ? "RPS - Spectator View" : "RPS - Game Area");

            if (instance.statusLabel != null) {
                if (isSpectator) {
                    instance.statusLabel.setText("Status: Spectator");
                } else if (isEliminated) {
                    instance.statusLabel.setText("Status: Eliminated");
                } else {
                    instance.statusLabel.setText("Status: Playing");
                }
            }

            if (isSpectator) {
                instance.setSpectatorMode(true);
            } else if (isEliminated) {
                instance.setEliminatedView();
            } else if (isPending) {
                if (!instance.hasPicked) {
                    instance.enableButtons();
                    instance.resetGameOverLabel();
                } else {
                    instance.disableButtons();
                    if (!instance.choiceCooldownEnabled) {
                        instance.stopTimer();
                    }
                }
            }
        }
        instance.updateAwayButtonVisibility();
    }


    private void handlePick(ActionEvent e) {
        String choice = e.getActionCommand().toLowerCase();

        if (!choiceCooldownEnabled && hasPicked) {
            logEvent("Cooldown is OFF. Your first choice is final. You cannot change it.");
            return;
        }

        Client.INSTANCE.sendChoice(choice);
        hasPicked = true;

        if (!choiceCooldownEnabled) {
            disableButtons();
            stopTimer();
            logEvent("Cooldown is OFF. Your first choice is final.");
        } else {
            disableButtons();
            logEvent("Cooldown is ON. You may change your choice after 2 seconds.");
            scheduleCooldownUnlock();
        }
    }

    private void enableButtons() {
        User self = Client.INSTANCE.getKnownClients().get(Client.INSTANCE.getClientId());

        if (isEliminated() || isAway || isSpectator || (self != null && self.isSpectator())) {
            disableButtons();

            if (isSpectator || (self != null && self.isSpectator())) {
                logEvent("You are a spectator and cannot play.");
            } else if (isAway) {
                logEvent("You are currently marked as away and cannot play until the next round.");
            }
            return;
        }

        rockButton.setEnabled(true);
        paperButton.setEnabled(true);
        scissorsButton.setEnabled(true);

        if (lizardButton != null) lizardButton.setEnabled(true);
        if (spockButton != null) spockButton.setEnabled(true);

        hasPicked = false;
    }

    public void disableButtons() {
        rockButton.setEnabled(false);
        paperButton.setEnabled(false);
        scissorsButton.setEnabled(false);
        if (lizardButton != null) lizardButton.setEnabled(false);
        if (spockButton != null) spockButton.setEnabled(false);
    }

    private void startTimer(int seconds) {
        stopTimer();
        timeLeft = seconds;
        timerLabel.setText("Time left: " + timeLeft);

        countdownTimer = new Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("Time left: " + timeLeft);
            if (timeLeft <= 0) {
                stopTimer();
                disableButtons();
                hasPicked = true;
                roundActive = false;
            }
        });
        countdownTimer.start();
    }


    public void stopTimer() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    public void resetGameOverLabel() {
        gameOverLabel.setVisible(false);
    }

    public void setHasPicked(boolean picked) {
        this.hasPicked = picked;
    }

    public boolean hasPicked() {
        return hasPicked;
    }

    public void updatePostGameCountdownLabel(String text) {
        SwingUtilities.invokeLater(() -> {
            if (hasPicked) return;
            postGameCountdownLabel.setText(text);
        });
    }

    public void resetForNewRound(int time) {
        postGamePanel.setVisible(false);
        readyStatusPanel.setVisible(false);
        gameOverLabel.setVisible(false);

        timeLeft = time;
        if (timerLabel != null) {
            timerLabel.setText("Time left: " + time);
        }
        if (postGameCountdownLabel != null) {
            postGameCountdownLabel.setText("");
        }

        hasPicked = false;
        roundActive = true;

        if (cooldownUnlockTimer != null) {
            cooldownUnlockTimer.stop();
            cooldownUnlockTimer = null;
        }

        disableButtons();

        startTimer(time);

        revalidate();
        repaint();

        updateAwayButtonVisibility();
    }


    public void showGameOver() {
        User self = Client.INSTANCE.getKnownClients().get(Client.INSTANCE.getClientId());
        boolean isSpectator = self != null && self.isSpectator();
        if (isSpectator) {
            stopTimer();

            if (cooldownUnlockTimer != null) {
                cooldownUnlockTimer.stop();
                cooldownUnlockTimer = null;
            }

            roundActive = false;
            disableButtons();
            gameOverLabel.setVisible(true);

            postGamePanel.setVisible(false);
            readyStatusPanel.setVisible(false);

            revalidate();
            repaint();
            return;
        }

        stopTimer();

        if (cooldownUnlockTimer != null) {
            cooldownUnlockTimer.stop();
            cooldownUnlockTimer = null;
        }

        roundActive = false;

        disableButtons();
        gameOverLabel.setVisible(true);

        if (postGameCountdownTimer != null) {
            postGameCountdownTimer.stop();
            postGameCountdownTimer = null;
        }

        postGameTimeLeft = 15;
        countdownRunning = false;

        postGameCountdownLabel.setText("Play again? Time left: " + postGameTimeLeft);
        postGamePanel.setVisible(true);
        updateAwayButtonVisibility();
        readyStatusPanel.setVisible(true);
        playAgainButton.setText("Play Again");
        playAgainButton.setEnabled(true);

        countdownRunning = true;
        postGameCountdownTimer = new Timer(1000, e -> {
            try {
                postGameTimeLeft--;
                if (postGameTimeLeft <= 0) {
                    postGameCountdownTimer.stop();
                    postGameCountdownTimer = null;
                    countdownRunning = false;
                    new Thread(this::kickToLobby).start();
                } else {
                    postGameCountdownLabel.setText("Play again? Time left: " + postGameTimeLeft);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        postGameCountdownTimer.start();
        revalidate();
        repaint();
    }



    private void handlePlayAgain() {
        playAgainButton.setText("Waiting...");
        playAgainButton.setEnabled(false);

        Client.INSTANCE.sendReadyStatus(true);
        postGameCountdownLabel.setText("Waiting for all players to be READY...");

        if (postGameCountdownTimer != null) {
            postGameCountdownTimer.stop();
            postGameCountdownTimer = null;
        }
    }

    private void handleReturnToLobby() {
        if (postGameCountdownTimer != null) {
            postGameCountdownTimer.stop();
            postGameCountdownTimer = null;
            countdownRunning = false;
        }

        postGameTimeLeft = 15;
        new Thread(() -> kickToLobby()).start();
    }

    public void kickToLobby() {
        roundActive = false;

        if (cooldownUnlockTimer != null) {
            cooldownUnlockTimer.stop();
            cooldownUnlockTimer = null;
        }

        stopTimer();
        countdownRunning = false;

        readyStatusMap.put(Client.INSTANCE.getClientId(), false);
        readyStatusListModel.clear();
        readyStatusMap.forEach((id, ready) -> {
            String name = id == Client.INSTANCE.getClientId() ? "You" : getDisplayName(id, null);
            readyStatusListModel.addElement(name + ": NOT READY");
        });

        postGamePanel.setVisible(false);
        readyStatusPanel.setVisible(false);
        gameOverLabel.setVisible(false);

        Point location;
        try {
            location = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            location = null;
        }

        Point finalLocation = location;
        SwingUtilities.invokeLater(() -> {
            this.dispose();
            Client.INSTANCE.sendRoomLeave();
            Client.INSTANCE.leaveRoomAndReturnToLobby(finalLocation);
        });
    }


    public void updateReadyStatus(long clientId, boolean isReady, String clientName) {
        readyStatusMap.put(clientId, isReady);

        SwingUtilities.invokeLater(() -> {
            readyStatusListModel.clear();
            readyStatusMap.forEach((id, ready) -> {
                String name = id == Client.INSTANCE.getClientId() ? "You" : getDisplayName(id, clientName);
                String statusLabel;
                User userObj = Client.INSTANCE.getKnownClients().get(id);
                if (userObj != null && userObj.isAway()) {
            statusLabel = "AWAY";
            readyStatusListModel.addElement(name + ": " + statusLabel);
        } else {
            statusLabel = ready ? "READY" : "NOT READY";
            readyStatusMap.put(id, ready);
            readyStatusListModel.addElement(name + ": " + statusLabel);
        }


            });
            checkIfAllReady();
        });
    }

    private String getDisplayName(long id, String fallbackName) {
        User fallbackUser = new User();
        fallbackUser.setClientId(id);
        fallbackUser.setClientName(fallbackName != null ? fallbackName : "Unknown#" + id);
        return Client.INSTANCE.getKnownClients().getOrDefault(id, fallbackUser).getClientName();
    }

    public boolean checkIfAllReady() {
        if (readyStatusMap.isEmpty()) return false;

        boolean allReady = true;

        readyStatusListModel.clear();
        for (Map.Entry<Long, Boolean> entry : readyStatusMap.entrySet()) {
            Long clientId = entry.getKey();
            boolean isReady = entry.getValue();

            User user = Client.INSTANCE.getKnownClients().get(clientId);
            String name = (clientId == Client.INSTANCE.getClientId()) ? "You" : getDisplayName(clientId, null);

            if (user != null && user.isAway()) {
                readyStatusListModel.addElement(name + ": AWAY");
                continue;
            }

            if (!isReady) {
                allReady = false;
            }

            String status = isReady ? "READY" : "NOT READY";
            readyStatusListModel.addElement(name + ": " + status);
        }

        if (!allReady) {
            postGameCountdownLabel.setText("Waiting for all players to be READY...");
            return false;
        }

        postGameCountdownLabel.setText("All players ready! Restarting game...");

        if (countdownRunning) {
            postGamePanel.setVisible(false);
            readyStatusPanel.setVisible(false);
            gameOverLabel.setVisible(false);

            countdownRunning = false;
            readyStatusMap.clear();

            playAgainButton.setText("Play Again");
            playAgainButton.setEnabled(true);

            enableButtons();
            startTimer(15);

            revalidate();
            repaint();
        }

        return true;
    }

    public void removeReadyStatus(long clientId) {
        readyStatusMap.remove(clientId);

        SwingUtilities.invokeLater(() -> {
            readyStatusListModel.clear();
            readyStatusMap.forEach((id, ready) -> {
                String name = id == Client.INSTANCE.getClientId() ? "You" : getDisplayName(id, null);
                readyStatusListModel.addElement(name + ": " + (ready ? "READY" : "NOT READY"));
            });
        });
    }

    public void stopPostGameCountdown() {
        if (postGameCountdownTimer != null) {
            postGameCountdownTimer.stop();
            postGameCountdownTimer = null;
            countdownRunning = false;
        }
    }

    public void resetAllClientStatus() {
        readyStatusMap.clear();
        readyStatusListModel.clear();
        hasPicked = false;
        stopTimer();
        disableButtons();
        gameOverLabel.setVisible(false);
    }

    public void logEvent(String message) {
        if (gameEventsPanel != null) {
            gameEventsPanel.addEvent(message);
        }
    }

    public void setGameMode(String mode) {
        this.currentGameMode = mode.toUpperCase();
        updateMoveButtons();
    }

    // UCID: mramos2001
    // Date: 2025-08-05
    // Description: Enables Lizard and Spock buttons if game mode is RPS5 or one of the RPS5-conditional variants

    private void updateMoveButtons() {
        String mode = currentGameMode.toUpperCase();

        boolean showExtras = mode.equals("RPS5") || mode.equals("RPS5 - ALWAYS") || 
            (mode.equals("RPS5 - AT 3 PLAYERS") && Client.INSTANCE.getKnownClients().size() <= 3);

        if (lizardButton != null) {
            lizardButton.setVisible(showExtras);
            lizardButton.setEnabled(showExtras);
        }

        if (spockButton != null) {
            spockButton.setVisible(showExtras);
            spockButton.setEnabled(showExtras);
        }

        revalidate();
        repaint();
    }

    private Timer cooldownUnlockTimer;

    public void scheduleCooldownUnlock() {
        if (cooldownUnlockTimer != null) {
            cooldownUnlockTimer.stop();
        }

        cooldownUnlockTimer = new Timer(2000, e -> {
            if (choiceCooldownEnabled && hasPicked && roundActive) {
                enableButtons();
                logEvent("You can now change your choice.");
            }
            cooldownUnlockTimer = null;
        });

        cooldownUnlockTimer.setRepeats(false);
        cooldownUnlockTimer.start();
    }



    public void setChoiceCooldownEnabled(boolean enabled) {
        this.choiceCooldownEnabled = enabled;
        hasLoggedCooldownStatus = false;
    }

    public GameEventsPanel getGameEventsPanel() {
        return gameEventsPanel;
    }

    public UserListPanel getUserListPanel() {
        return userListPanel;
    }

    public void updateAwayButtonVisibility() {
        if (awayToggleButton == null) return;

        boolean gameOverVisible = postGamePanel != null && postGamePanel.isVisible();
        boolean eliminated = isEliminated();
        boolean returningFromAway = isAway;

        UserStatus us = userListPanel.getUserStatusById(Client.INSTANCE.getClientId());
        boolean isSpectator = us != null && us.getName().contains("(Spectator)");

        boolean shouldShow = (gameOverVisible || returningFromAway) && !eliminated && !isSpectator;

        awayToggleButton.setVisible(shouldShow);
        awayToggleButton.setEnabled(shouldShow);
    }

    public boolean isEliminated() {
        return statusLabel != null && statusLabel.getText().contains("Eliminated");
    }

    public void setEliminatedView() {
        disableButtons();
        playAgainButton.setVisible(true);
        returnToLobbyButton.setVisible(true);
        statusLabel.setText("Status: Eliminated");
        setTitle("RPS - Eliminated View");
    }

    public void initSpectatorPanel() {
        spectatorPanel = new JPanel(new FlowLayout());
        spectatorReturnButton = new JButton("Return to Lobby");
        spectatorReturnButton.addActionListener(e -> {
            new Thread(this::kickToLobby).start();
        });
        spectatorPanel.add(spectatorReturnButton);
    }

    private boolean isSpectator = false;

    public void setSpectatorMode(boolean spectator) {
        this.isSpectator = spectator;
        hasPicked = spectator;

        Container contentPane = getContentPane();

        if (spectator) {
            if (gameViewPanel != null) {
                gameViewPanel.setVisible(false);
            }
            stopTimer();

            if (rockButton != null) {
                rockButton.setVisible(false);
                rockButton.setEnabled(false);
            }
            if (paperButton != null) {
                paperButton.setVisible(false);
                paperButton.setEnabled(false);
            }
            if (scissorsButton != null) {
                scissorsButton.setVisible(false);
                scissorsButton.setEnabled(false);
            }
            if (lizardButton != null) {
                lizardButton.setVisible(false);
                lizardButton.setEnabled(false);
            }
            if (spockButton != null) {
                spockButton.setVisible(false);
                spockButton.setEnabled(false);
            }
            if (timerLabel != null) {
                timerLabel.setVisible(false);
            }

            if (awayToggleButton != null) {
                awayToggleButton.setVisible(false);
                awayToggleButton.setEnabled(false);
            }

            if (playAgainButton != null) {
                playAgainButton.setVisible(false);
                playAgainButton.setEnabled(false);
            }

            if (returnToLobbyButton != null) {
                returnToLobbyButton.setVisible(false);
                returnToLobbyButton.setEnabled(false);
            }

            if (spectatorPanel != null) {
                spectatorPanel.setVisible(true);
            }

            statusLabel.setText("Status: Spectator - Watching Only");
        } else {
            if (gameViewPanel != null) {
                gameViewPanel.setVisible(true);
            }

            if (spectatorPanel != null) {
                spectatorPanel.setVisible(false);
            }

            if (rockButton != null) {
                rockButton.setVisible(true);
                rockButton.setEnabled(true);
            }
            if (paperButton != null) {
                paperButton.setVisible(true);
                paperButton.setEnabled(true);
            }
            if (scissorsButton != null) {
                scissorsButton.setVisible(true);
                scissorsButton.setEnabled(true);
            }
            if (lizardButton != null) {
                lizardButton.setVisible(true);
                lizardButton.setEnabled(true);
            }
            if (spockButton != null) {
                spockButton.setVisible(true);
                spockButton.setEnabled(true);
            }
            if (timerLabel != null) {
                timerLabel.setVisible(true);
            }

            if (awayToggleButton != null) {
                awayToggleButton.setVisible(true);
                awayToggleButton.setEnabled(true);
            }

            if (playAgainButton != null) {
                playAgainButton.setVisible(true);
                playAgainButton.setEnabled(true);
            }

            if (returnToLobbyButton != null) {
                returnToLobbyButton.setVisible(true);
                returnToLobbyButton.setEnabled(true);
            }

            statusLabel.setText("Status: Playing");
        }

        revalidate();
        repaint();
    }

}
