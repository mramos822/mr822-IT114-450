package Project.Client;

import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.*;

public class ConnectionUI extends JFrame {
    private JTextField nameField;
    private JTextField hostField;
    private JTextField portField;
    private JButton connectButton;

    public ConnectionUI() {
        setTitle("Connect to RPS Server");
        setSize(350, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(4, 2, 5, 5));

        add(new JLabel("Username:"));
        nameField = new JTextField();
        add(nameField);

        add(new JLabel("Host:"));
        hostField = new JTextField("localhost");
        add(hostField);

        add(new JLabel("Port:"));
        portField = new JTextField("3000");
        add(portField);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(this::handleConnect);
        add(connectButton);

        add(new JLabel()); // filler

        // Add Enter key support to text fields
        nameField.addActionListener(this::handleConnect);
        hostField.addActionListener(this::handleConnect);
        portField.addActionListener(this::handleConnect);

        setVisible(true);
    }

    private void handleConnect(ActionEvent e) {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        int port;

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username is required.");
            return;
        }

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Host is required.");
            return;
        }

        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port number.");
            return;
        }

        try {
            Client.INSTANCE.processClientCommand("/name " + name);
            Client.INSTANCE.processClientCommand("/connect " + host + ":" + port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        // ✅ Capture location before disposing
        Point location = null;
        try {
            location = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            location = null;
        }

        // ✅ Dispose current window
        this.dispose();

        // ✅ Open Login UI in same location
        Point finalLocation = location; // effectively final for lambda
        SwingUtilities.invokeLater(() -> {
            LoginRoomUI loginUI = new LoginRoomUI(finalLocation);
            loginUI.setSize(800, 300);
            loginUI.setVisible(true);

            // Optional: Bring it to front
            loginUI.setAlwaysOnTop(true);
            loginUI.toFront();
            loginUI.requestFocus();
            loginUI.setAlwaysOnTop(false);
        });
    }
}
