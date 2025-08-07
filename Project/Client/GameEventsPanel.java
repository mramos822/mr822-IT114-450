package Project.Client;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

public class GameEventsPanel extends JPanel {
    private JTextPane eventPane;
    private StyledDocument doc;

    public GameEventsPanel() {
        setLayout(new BorderLayout());

        eventPane = new JTextPane();
        eventPane.setEditable(false);
        eventPane.setFont(new Font("Monospaced", Font.PLAIN, 11));

        doc = eventPane.getStyledDocument();
        initStyles(doc);

        JScrollPane scrollPane = new JScrollPane(eventPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        setPreferredSize(new Dimension(800, 250));
        setBorder(BorderFactory.createTitledBorder("Game Events"));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void addEvent(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String lower = message.toLowerCase();

                if (lower.contains("has picked")) {
                    appendStyled(message, "picked");
                } else if (lower.contains("eliminated")) {
                    appendStyled(message, "eliminated");
                } else if (lower.contains("tie") || lower.contains("draw")) {
                    appendStyled(message, "tie");
                } else if (lower.contains("wins")) {
                    appendStyled(message, "win");
                } else if (lower.contains("scoreboard") || lower.contains("round")) {
                    appendStyled(message, "info");
                } else {
                    appendStyled(message, "default");
                }

                // Highlight username (first word if it's a name)
                highlightUsername(message);
                eventPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void appendStyled(String message, String style) throws BadLocationException {
        doc.insertString(doc.getLength(), message + "\n", doc.getStyle(style));
    }

    private void highlightUsername(String message) throws BadLocationException {
        String[] parts = message.split(" ");
        if (parts.length == 0) return;

        String name = parts[0];
        if (!Character.isUpperCase(name.charAt(0)) || name.toLowerCase().contains("round")) return;

        int start = doc.getText(0, doc.getLength()).lastIndexOf(name);
        if (start >= 0 && start + name.length() <= doc.getLength()) {
            Style style = doc.getStyle("gold");
            if (style != null) {
                doc.setCharacterAttributes(start, name.length(), style, false);
            }
        }
    }


    private void initStyles(StyledDocument doc) {
        Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        Style picked = doc.addStyle("picked", def);
        StyleConstants.setForeground(picked, new Color(0, 153, 0)); // Green

        Style eliminated = doc.addStyle("eliminated", def);
        StyleConstants.setForeground(eliminated, Color.RED);

        Style tie = doc.addStyle("tie", def);
        StyleConstants.setForeground(tie, Color.BLUE);

        Style win = doc.addStyle("win", def);
        StyleConstants.setForeground(win, new Color(0, 128, 0));
        StyleConstants.setBold(win, true);

        Style info = doc.addStyle("info", def);
        StyleConstants.setForeground(info, Color.DARK_GRAY);
        StyleConstants.setItalic(info, true);

        Style gold = doc.addStyle("gold", def);
        StyleConstants.setForeground(gold, new Color(212, 175, 55)); // Gold

        Style defaultStyle = doc.addStyle("default", def);
        StyleConstants.setForeground(defaultStyle, Color.BLACK);
    }

    public void clearEvents() {
        eventPane.setText("");
    }
}
