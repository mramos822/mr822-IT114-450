// UCID: mramos2001
// Date: 2025-08-06
// Description: Ready check UI with spectator toggle disabling Ready button and sending spectator payload.

package Project.Client;

import Project.Common.GameSettingsPayload;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.User;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class ReadyCheckUI extends JFrame {
    public static ReadyCheckUI instance;

    private DefaultListModel<String> clientListModel;
    private JList<String> clientList;
    private JButton readyButton;
    private JButton returnButton;
    private JLabel roomLabel;
    private boolean isReady = false;
    private final Map<Long, Boolean> readyStatusMap = new ConcurrentHashMap<>();

    private JRadioButton classicButton;
    private JRadioButton option1Button;
    private JRadioButton option2Button;
    private JRadioButton speedDropButton;
    private ButtonGroup extraOptionsGroup;
    private JCheckBox allowChoiceChangeToggle;
    private boolean isHost = false;
    private JPanel roomSettingsPanel;
    private JLabel selectedModeLabel;
    private JLabel allowChangeLabel;
    private JCheckBox spectatorToggle;

    public ReadyCheckUI(Point location) {
        this.isHost = Client.INSTANCE.isHost();
        instance = this;

        setTitle("RPS - Ready Check");
        setSize(500, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        if (location != null) {
            setLocation(location);
        } else {
            setLocationRelativeTo(null);
        }

        // === Top: Room name ===
        String currentRoom = Client.INSTANCE.getCurrentRoomName();
        roomLabel = new JLabel("Room: " + currentRoom, SwingConstants.CENTER);
        roomLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(roomLabel, BorderLayout.NORTH);

        // === Center: List of clients ===
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        JScrollPane scrollPane = new JScrollPane(clientList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Players in Room"));
        add(scrollPane, BorderLayout.CENTER);

        // === East: Host-only toggle panel for game mode selection ===
        if (isHost) {
            JPanel togglePanel = new JPanel();
            togglePanel.setLayout(new BoxLayout(togglePanel, BoxLayout.Y_AXIS));
            togglePanel.setBorder(BorderFactory.createTitledBorder("Game Mode"));

            classicButton = new JRadioButton("Classic RPS Only");
            option1Button = new JRadioButton("RPS-5: Always");
            option2Button = new JRadioButton("RPS-5: At 3 Players");
            speedDropButton = new JRadioButton("Speed Drop Mode");

            allowChoiceChangeToggle = new JCheckBox("Allow Changing Choice");
            allowChoiceChangeToggle.setEnabled(true);
            allowChoiceChangeToggle.setSelected(false);

            extraOptionsGroup = new ButtonGroup();
            extraOptionsGroup.add(classicButton);
            extraOptionsGroup.add(option1Button);
            extraOptionsGroup.add(option2Button);
            extraOptionsGroup.add(speedDropButton);
            classicButton.setSelected(true);

            togglePanel.add(classicButton);
            togglePanel.add(Box.createRigidArea(new Dimension(0, 5)));
            togglePanel.add(option1Button);
            togglePanel.add(Box.createRigidArea(new Dimension(0, 5)));
            togglePanel.add(option2Button);
            togglePanel.add(Box.createRigidArea(new Dimension(0, 5)));
            togglePanel.add(speedDropButton);

            togglePanel.add(Box.createRigidArea(new Dimension(0, 10)));
            togglePanel.add(allowChoiceChangeToggle);

            togglePanel.setPreferredSize(new Dimension(240, 180));
            add(togglePanel, BorderLayout.EAST);

            ActionListener modeListener = e -> {
                try {
                    String selectedOption;
                    if (option1Button.isSelected()) {
                        selectedOption = "RPS5 - ALWAYS";
                    } else if (option2Button.isSelected()) {
                        selectedOption = "RPS5 - AT 3 PLAYERS";
                    } else if (speedDropButton.isSelected()) {
                        selectedOption = "SPEED DROP MODE";
                    } else {
                        selectedOption = "CLASSIC MODE";
                    }

                    GameSettingsPayload togglePayload = new GameSettingsPayload();
                    togglePayload.setPayloadType(PayloadType.GAME_SETTINGS);
                    togglePayload.setClientId(Client.INSTANCE.getClientId());
                    togglePayload.setGameMode(selectedOption);

                    boolean allowChanges = allowChoiceChangeToggle.isSelected();
                    togglePayload.setAllowChoiceChanges(allowChanges);
                    togglePayload.setChoiceCooldown(allowChanges);

                    Client.INSTANCE.sendToServer(togglePayload);

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error sending game options.");
                    ex.printStackTrace();
                }
            };

            classicButton.addActionListener(modeListener);
            option1Button.addActionListener(modeListener);
            option2Button.addActionListener(modeListener);
            speedDropButton.addActionListener(modeListener);
            allowChoiceChangeToggle.addActionListener(modeListener);
        } else {
            roomSettingsPanel = createRoomSettingsPanel();
            roomSettingsPanel.setPreferredSize(new Dimension(210, 100));
            add(roomSettingsPanel, BorderLayout.EAST);
        }

        // === Bottom: Spectator toggle + Buttons ===
        JPanel bottomPanel = new JPanel(new GridLayout(3, 1, 10, 10));

        spectatorToggle = new JCheckBox("Join as Spectator");
        spectatorToggle.setFont(new Font("Arial", Font.PLAIN, 12));
        bottomPanel.add(spectatorToggle);

        // NEW: Spectator toggle handling
        spectatorToggle.addActionListener(e -> {
            try {
                boolean spectator = spectatorToggle.isSelected();
                readyButton.setEnabled(!spectator); // disable Ready when spectator

                Payload p = new Payload();
                p.setPayloadType(PayloadType.SPECTATOR);
                p.setClientId(Client.INSTANCE.getClientId());
                p.setMessage(Boolean.toString(spectator));

                Client.INSTANCE.sendToServer(p);

                User self = Client.INSTANCE.getKnownClients().get(Client.INSTANCE.getClientId());
                if (self != null) {
                    self.setSpectator(spectator);
                }
                ReadyCheckUI.updateStatusForUser(Client.INSTANCE.getClientId(), false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error sending spectator toggle.");
                ex.printStackTrace();
            }
        });

        readyButton = new JButton("I'm Ready");
        readyButton.setFont(new Font("Arial", Font.BOLD, 14));
        readyButton.addActionListener(this::handleReadyToggle);
        bottomPanel.add(readyButton);

        returnButton = new JButton("Return to Lobby");
        returnButton.setFont(new Font("Arial", Font.PLAIN, 12));
        returnButton.addActionListener(this::handleReturnToLobby);
        bottomPanel.add(returnButton);

        add(bottomPanel, BorderLayout.SOUTH);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(ReadyCheckUI.this::updateUserList);
            }
        }, 0, 1000);

        if (isHost) {
            classicButton.setSelected(true);
            allowChoiceChangeToggle.setSelected(false);

            try {
                GameSettingsPayload defaultPayload = new GameSettingsPayload();
                defaultPayload.setPayloadType(PayloadType.GAME_SETTINGS);
                defaultPayload.setClientId(Client.INSTANCE.getClientId());
                defaultPayload.setGameMode("CLASSIC MODE");
                defaultPayload.setAllowChoiceChanges(false);
                defaultPayload.setChoiceCooldown(false);

                Client.INSTANCE.sendToServer(defaultPayload);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to send default game settings.");
            }
        }

        setVisible(true);
    }

    private void handleReadyToggle(ActionEvent e) {
        if (spectatorToggle.isSelected()) {
            return; // spectators can't ready up
        }
        isReady = !isReady;
        readyButton.setText(isReady ? "I'm NOT Ready" : "I'm Ready");

        try {
            if (isHost) {
                boolean enableOptions = !isReady;
                if (classicButton != null) classicButton.setEnabled(enableOptions);
                if (option1Button != null) option1Button.setEnabled(enableOptions);
                if (option2Button != null) option2Button.setEnabled(enableOptions);
                if (speedDropButton != null) speedDropButton.setEnabled(enableOptions);
                if (allowChoiceChangeToggle != null) allowChoiceChangeToggle.setEnabled(enableOptions);
            }

            Payload readyPayload = new Payload();
            readyPayload.setPayloadType(PayloadType.READY);
            readyPayload.setClientId(Client.INSTANCE.getClientId());
            readyPayload.setMessage(isReady ? "ready" : "not_ready");
            Client.INSTANCE.sendToServer(readyPayload);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error sending READY payload.");
            ex.printStackTrace();
        }

        ReadyCheckUI.updateStatusForUser(Client.INSTANCE.getClientId(), isReady);
    }

    private void updateUserList() {
        clientListModel.clear();
        ConcurrentHashMap<Long, User> users = Client.INSTANCE.getKnownClients();
        long selfId = Client.INSTANCE.getClientId();

        java.util.List<String> others = new ArrayList<>();
        String selfEntry = null;

        for (User user : users.values()) {
            if (user == null) continue;

            long id = user.getClientId();
            String name = user.getClientName();
            if (name == null || name.isBlank()) {
                name = "Unknown#" + id;
                user.setClientName(name);
            }

            boolean ready = readyStatusMap.getOrDefault(id, false);
            String status;
            if (user.isSpectator()) {
                status = "SPECTATOR";
            } else {
                status = ready ? "READY" : "NOT READY";
            }

            if (id == selfId) {
                selfEntry = String.format("%s (YOU) - %s", name, status);
            } else {
                others.add(String.format("%s - %s", name, status));
            }
        }

        others.sort(String::compareToIgnoreCase);

        if (selfEntry != null) clientListModel.addElement(selfEntry);
        for (String other : others) clientListModel.addElement(other);
    }

    private void handleReturnToLobby(ActionEvent e) {
        Point location = null;
        try {
            location = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            location = null;
        }

        try {
            Client.INSTANCE.processClientCommand("/leaveroom");
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to leave room.");
        }

        this.dispose();
        Client.INSTANCE.leaveRoomAndReturnToLobby(location);
    }

    public void setReadyStatus(long clientId, boolean isReady) {
        User user = Client.INSTANCE.getKnownClients().get(clientId);
        if (user != null && user.isSpectator()) {
            readyStatusMap.put(clientId, false);
            if (clientId == Client.INSTANCE.getClientId()) {
                readyButton.setEnabled(false);
                spectatorToggle.setSelected(true);
            }
        } else {
            readyStatusMap.put(clientId, isReady);
            if (clientId == Client.INSTANCE.getClientId()) {
                readyButton.setEnabled(true);
                spectatorToggle.setSelected(false);
            }
        }
        SwingUtilities.invokeLater(this::updateUserList);
    }


    public static void updateStatusForUser(long id, boolean status) {
        if (instance != null) {
            if (!Client.INSTANCE.getKnownClients().containsKey(id)) {
                User user = new User();
                user.setClientId(id);
                user.setClientName("Unknown#" + id);
                Client.INSTANCE.getKnownClients().put(id, user);
            }
            instance.setReadyStatus(id, status);
        }
    }

    private JPanel createRoomSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Room Settings"));

        selectedModeLabel = new JLabel("Mode: Loading...");
        selectedModeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectedModeLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, selectedModeLabel.getPreferredSize().height));

        allowChangeLabel = new JLabel("Allow Changing Choice: Loading...");
        allowChangeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowChangeLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, allowChangeLabel.getPreferredSize().height));

        panel.add(selectedModeLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(allowChangeLabel);

        return panel;
    }

    public void updateRoomSettingsDisplay(String mode, boolean allowChange) {
        if (selectedModeLabel != null) {
            selectedModeLabel.setText("Mode: " + mode);
        }
        if (allowChangeLabel != null) {
            allowChangeLabel.setText("Allow Changing Choice: " + (allowChange ? "Yes" : "No"));
        }

        if (roomSettingsPanel != null) {
            roomSettingsPanel.revalidate();
            roomSettingsPanel.repaint();
        }
    }

    public static ReadyCheckUI getInstanceIfExists() {
        return instance;
    }
    
    public void setSpectatorMode(boolean isSpectator) {
        readyButton.setEnabled(!isSpectator);
        spectatorToggle.setSelected(isSpectator);
        spectatorToggle.setEnabled(!isSpectator);
    }

}
