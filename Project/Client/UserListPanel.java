package Project.Client;

import Project.Common.UserStatus;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class UserListPanel extends JPanel {
    private DefaultListModel<UserStatusDisplay> listModel;
    private JList<UserStatusDisplay> userList;

    public UserListPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300, 0));
        setBorder(BorderFactory.createTitledBorder("Players"));

        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setCellRenderer(new UserStatusRenderer());

        add(new JScrollPane(userList), BorderLayout.CENTER);
    }

    public void updateUserList(List<UserStatus> statuses, Set<Long> eliminatedIds, Set<Long> pendingIds) {
        listModel.clear();

        List<UserStatus> mutableStatuses = new ArrayList<>(statuses);
        mutableStatuses.sort((a, b) -> {
            if (a.isSpectator() && !b.isSpectator()) return 1;
            if (!a.isSpectator() && b.isSpectator()) return -1;

            int cmp = Integer.compare(b.getPoints(), a.getPoints());
            return cmp != 0 ? cmp : a.getName().compareToIgnoreCase(b.getName());
        });

        for (UserStatus status : mutableStatuses) {
            String userName = status.getName();
            long id = status.getId();
            int points = status.getPoints();

            if (status.isSpectator()) {
                userName += " (Spectator)";
            }

            StatusType type;
            if (eliminatedIds.contains(id)) {
                type = StatusType.ELIMINATED;
            } else if (pendingIds.contains(id)) {
                type = StatusType.PENDING;
            } else {
                type = StatusType.READY;
            }

            UserStatusDisplay display = new UserStatusDisplay(userName, id, points, type);
            display.setAway(status.isAway());
            listModel.addElement(display);
        }

        userList.repaint();
    }




    public void setAwayStatus(long userId, boolean isAway) {
        for (int i = 0; i < listModel.size(); i++) {
            UserStatusDisplay display = listModel.get(i);
            if (display.id == userId) {
                display.setAway(isAway);
                listModel.set(i, display);
                break;
            }
        }
        userList.repaint();
    }

    private enum StatusType {
        READY, PENDING, ELIMINATED
    }

    private static class UserStatusDisplay {
        String name;
        long id;
        int points;
        StatusType status;
        boolean isAway = false;

        public UserStatusDisplay(String name, long id, int points, StatusType status) {
            this.name = name;
            this.id = id;
            this.points = points;
            this.status = status;
        }

        public void setAway(boolean away) {
            this.isAway = away;
        }

        public boolean isAway() {
            return isAway;
        }

        @Override
        public String toString() {
            return String.format("%s (ID: %d) - %d pts", name, id, points);
        }
    }

    private static class UserStatusRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof UserStatusDisplay display) {
                label.setText(display.toString());

                if (display.isAway()) {
                    label.setForeground(Color.GRAY);
                    label.setFont(label.getFont().deriveFont(Font.ITALIC));
                } else if (display.name.contains("(Spectator)")) {
                    label.setForeground(new Color(211, 211, 211));
                    label.setFont(label.getFont().deriveFont(Font.ITALIC));
                } else {
                    switch (display.status) {
                        case ELIMINATED -> label.setForeground(Color.RED);
                        case PENDING -> label.setForeground(Color.BLUE);
                        case READY -> label.setForeground(new Color(0, 128, 0));
                    }
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                }
            }

            return label;
        }
    }

    public UserStatus getUserStatusById(long id) {
        for (int i = 0; i < listModel.size(); i++) {
            UserStatusDisplay display = listModel.get(i);
            if (display.id == id) {
                UserStatus us = new UserStatus(display.name.replace(" (Spectator)", ""), display.id, display.points);
                us.setAway(display.isAway());
                boolean isSpectator = display.name.contains("(Spectator)");
                if (isSpectator) {
                    try {
                        java.lang.reflect.Field awayField = UserStatus.class.getDeclaredField("away");
                        awayField.setAccessible(true);
                        awayField.set(us, display.isAway());
                    } catch (Exception e) {
                    }
                }
                return us;
            }
        }
        return null;
    }


}
