// UCID: mramos2001
// Date: 2025-07-30
// Description: Room selection UI with staged room name input, join feedback, and live room list

package Project.Client;

import Project.Common.User;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class LoginRoomUI extends JFrame {
    private JTextField roomNameField;
    private JLabel roomNameLabel;
    private JLabel connectionLabel;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private boolean enteringCreate = false;
    private boolean enteringJoin = false;
    private JPanel inputPanel;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;

    public LoginRoomUI(Point location) {
        setTitle("Join or Create Room");
        setSize(700, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        if (location != null) {
            setLocation(location);
        } else {
            setLocationRelativeTo(null);
        }

        // === Left panel: user list ===
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Users in Lobby"));
        scrollPane.setPreferredSize(new Dimension(180, 300));
        add(scrollPane, BorderLayout.WEST);

        // === Right panel: room list ===
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setFont(new Font("Arial", Font.PLAIN, 14));
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedRoom = roomList.getSelectedValue();
                if (selectedRoom != null) {
                    roomNameField.setText(selectedRoom.replaceAll("\\s*\\(.*\\)", "").trim());
                }
            }
        });
        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(BorderFactory.createTitledBorder("Active Rooms"));
        roomScrollPane.setPreferredSize(new Dimension(180, 300));
        add(roomScrollPane, BorderLayout.EAST);

        // === Center form panel ===
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Room name input panel
        inputPanel = new JPanel(new BorderLayout(5, 5));
        roomNameLabel = new JLabel("Room Name:");
        roomNameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        roomNameField = new JTextField();
        roomNameField.setFont(new Font("Arial", Font.PLAIN, 14));
        inputPanel.add(roomNameLabel, BorderLayout.WEST);
        inputPanel.add(roomNameField, BorderLayout.CENTER);
        inputPanel.setVisible(false);

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(inputPanel, gbc);

        // Connection panel
        connectionLabel = new JLabel("Connected as: " + Client.INSTANCE.getClientName());
        connectionLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        gbc.gridy = 1;
        formPanel.add(connectionLabel, gbc);

        // Create Room Button
        createRoomButton = new JButton("Create Room");
        createRoomButton.setFont(new Font("Arial", Font.BOLD, 14));
        createRoomButton.addActionListener(this::handleCreateRoom);
        gbc.gridy = 2;
        formPanel.add(createRoomButton, gbc);

        // Join Room Button
        joinRoomButton = new JButton("Join Room");
        joinRoomButton.setFont(new Font("Arial", Font.BOLD, 14));
        joinRoomButton.addActionListener(this::handleJoinRoom);
        gbc.gridy = 3;
        formPanel.add(joinRoomButton, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Timer to refresh lobby user and room list every 1 second
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    refreshUserList();
                    refreshRoomList();
                });

                try {
                    Client.INSTANCE.processClientCommand("/listrooms");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, 0, 1000);
    }

    public static void updateStatusForUser(long id, boolean status) {
        ReadyCheckUI.updateStatusForUser(id, status);
    }

    private void handleCreateRoom(ActionEvent e) {
        if (!enteringCreate) {
            enteringCreate = true;
            enteringJoin = false;
            inputPanel.setVisible(true);
            roomNameField.setText("");
            createRoomButton.setText("Confirm Create");
            joinRoomButton.setText("Join Room");
            return;
        }

        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a room name.");
            return;
        }

        try {
            Client.INSTANCE.setRoomJoinCallback(new Client.RoomJoinCallback() {
                @Override
                public void onSuccess() {
                    proceedToReadyCheck();
                }

                @Override
                public void onFailure(String message) {
                    JOptionPane.showMessageDialog(LoginRoomUI.this, "Failed to create room: " + message);
                }
            });

            Client.INSTANCE.processClientCommand("/createroom " + roomName);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to create room: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void handleJoinRoom(ActionEvent e) {
        if (!enteringJoin) {
            enteringJoin = true;
            enteringCreate = false;
            inputPanel.setVisible(true);
            roomNameField.setText("");
            joinRoomButton.setText("Confirm Join");
            createRoomButton.setText("Create Room");
            return;
        }

        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a room name.");
            return;
        }

        try {
            Client.INSTANCE.setRoomJoinCallback(new Client.RoomJoinCallback() {
                @Override
                public void onSuccess() {
                    proceedToReadyCheck();
                }

                @Override
                public void onFailure(String message) {
                    JOptionPane.showMessageDialog(LoginRoomUI.this, "Failed to join room: " + message);
                }
            });

            Client.INSTANCE.processClientCommand("/joinroom " + roomName);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to join room: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void refreshUserList() {
        userListModel.clear();
        ConcurrentHashMap<Long, User> users = Client.INSTANCE.getKnownClients();
        for (User user : users.values()) {
            if (user.getClientName() != null) {
                userListModel.addElement(user.getClientName() + " (ID: " + user.getClientId() + ")");
            }
        }
    }

    private void refreshRoomList() {
        roomListModel.clear();
        List<String> rooms = Client.INSTANCE.getAvailableRooms();
        rooms.stream()
            .sorted((a, b) -> extractCount(b) - extractCount(a))
            .forEach(roomListModel::addElement);
    }

    private int extractCount(String roomDisplay) {
        try {
            int start = roomDisplay.indexOf('(');
            int end = roomDisplay.indexOf(' ');
            if (start >= 0 && end > start) {
                return Integer.parseInt(roomDisplay.substring(start + 1, end));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void proceedToReadyCheck() {
        Point location = null;
        try {
            location = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            location = null;
        }

        boolean isHost = Client.INSTANCE.getClientId() == Client.INSTANCE.getRoomCreatorId();

        new ReadyCheckUI(location);
        Client.INSTANCE.sendReadyStatus(false);
        this.dispose();
    }
}
