package Project.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Map;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.IllegalComponentStateException;
import java.util.Set;

import Project.Common.Command;
import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomAction;
import Project.Common.TextFX;
import Project.Common.User;
import Project.Common.TextFX.Color;
import Project.Common.PointsPayload;
import javax.swing.JOptionPane;
import Project.Common.RoomResultPayload;
import Project.Common.GameSettingsPayload;
import Project.Common.UserStatus;
import javax.crypto.Mac;



/**
 * Demoing bi-directional communication between client and server in a
 * multi-client scenario
 */
public enum Client {
    INSTANCE;

    {
        // statically initialize the client-side LoggerUtil
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024);
        config.setFileCount(1);
        config.setLogLocation("client.log");
        // Set the logger configuration
        LoggerUtil.INSTANCE.setConfig(config);
    }

    private boolean hasJoinedRoom = false;
    private User user;
    private PointsPayload pendingPointsPayload = null;
    private static GameAreaUI instance;
    private RoomResultPayload currentRoom;
    private String extraOptionMode = "NONE";
    private boolean isHost = false;
    private String pendingGameMode = "Classic Mode";
    private boolean pendingCooldown = true;
    private boolean isFirstRoundOfGame = true;


    private Socket server = null;
    private ObjectOutputStream out = null;
    private ObjectInputStream in = null;
    final Pattern ipAddressPattern = Pattern
            .compile("/connect\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{3,5})");
    final Pattern localhostPattern = Pattern.compile("/connect\\s+(localhost:\\d{3,5})");
    private volatile boolean isRunning = true; // volatile for thread-safe visibility
    private final ConcurrentHashMap<Long, User> knownClients = new ConcurrentHashMap<Long, User>();
    private User myUser = new User();
    private final Map<Long, Boolean> awayStatusMap = new ConcurrentHashMap<>();

    private void error(String message) {
        LoggerUtil.INSTANCE.severe(TextFX.colorize(String.format("%s", message), Color.RED));
    }

    // needs to be private now that the enum logic is handling this
    private Client() {
        LoggerUtil.INSTANCE.info("Client Created");
    }

    private RoomJoinCallback roomJoinCallback;
    private final ConcurrentHashMap<Long, String> clientIdToName = new ConcurrentHashMap<>();

    public void setRoomJoinCallback(RoomJoinCallback callback) {
        this.roomJoinCallback = callback;
    }


    public boolean isConnected() {
        if (server == null) {
            return false;
        }
        // https://stackoverflow.com/a/10241044
        // Note: these check the client's end of the socket connect; therefore they
        // don't really help determine if the server had a problem
        // and is just for lesson's sake
        return server.isConnected() && !server.isClosed() && !server.isInputShutdown() && !server.isOutputShutdown();
    }

    /**
     * Takes an IP address and a port to attempt a socket connection to a server.
     * 
     * @param address
     * @param port
     * @return true if connection was successful
     */
    private boolean connect(String address, int port) {
        try {
            server = new Socket(address, port);
            // channel to send to server
            out = new ObjectOutputStream(server.getOutputStream());
            // channel to listen to server
            in = new ObjectInputStream(server.getInputStream());
            LoggerUtil.INSTANCE.info("Client connected");
            // Use CompletableFuture to run listenToServer() in a separate thread
            CompletableFuture.runAsync(this::listenToServer);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isConnected();
    }

    /**
     * <p>
     * Check if the string contains the <i>connect</i> command
     * followed by an IP address and port or localhost and port.
     * </p>
     * <p>
     * Example format: 123.123.123.123:3000
     * </p>
     * <p>
     * Example format: localhost:3000
     * </p>
     * https://www.w3schools.com/java/java_regex.asp
     * 
     * @param text
     * @return true if the text is a valid connection command
     */
    private boolean isConnection(String text) {
        Matcher ipMatcher = ipAddressPattern.matcher(text);
        Matcher localhostMatcher = localhostPattern.matcher(text);
        return ipMatcher.matches() || localhostMatcher.matches();
    }

    /**
     * Controller for handling various text commands.
     * <p>
     * Add more here as needed
     * </p>
     * 
     * @param text
     * @return true if the text was a command or triggered a command
     * @throws IOException
     */
    public boolean processClientCommand(String text) throws IOException {
        boolean wasCommand = false;
        if (text.startsWith(Constants.COMMAND_TRIGGER)) {
            text = text.substring(1); // remove the /
            // System.out.println("Checking command: " + text);
            if (isConnection("/" + text)) {
                if (myUser.getClientName() == null || myUser.getClientName().isEmpty()) {
                    LoggerUtil.INSTANCE.warning(
                            TextFX.colorize("Please set your name via /name <name> before connecting", Color.RED));
                    return true;
                }
                // replaces multiple spaces with a single space
                // splits on the space after connect (gives us host and port)
                // splits on : to get host as index 0 and port as index 1
                String[] parts = text.trim().replaceAll(" +", " ").split(" ")[1].split(":");
                connect(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                sendClientName(myUser.getClientName());// sync follow-up data (handshake)
                wasCommand = true;
            } else if (text.startsWith(Command.NAME.command)) {
                text = text.replace(Command.NAME.command, "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE.warning(TextFX.colorize("This command requires a name as an argument", Color.RED));
                    return true;
                }
                myUser.setClientName(text);// temporary until we get a response from the server
                LoggerUtil.INSTANCE.info(TextFX.colorize(String.format("Name set to %s", myUser.getClientName()),
                        Color.YELLOW));
                wasCommand = true;
            } else if (text.equalsIgnoreCase(Command.LIST_USERS.command)) {
                LoggerUtil.INSTANCE.info(TextFX.colorize("Known clients:", Color.CYAN));
                knownClients.forEach((key, value) -> {
                    LoggerUtil.INSTANCE.info(TextFX.colorize(String.format("%s%s", value.getDisplayName(),
                            key == myUser.getClientId() ? " (you)" : ""), Color.CYAN));
                });
                wasCommand = true;
            } else if (Command.QUIT.command.equalsIgnoreCase(text)) {
                close();
                wasCommand = true;
            } else if (Command.DISCONNECT.command.equalsIgnoreCase(text)) {
                sendDisconnect();
                wasCommand = true;
            } else if (text.startsWith(Command.REVERSE.command)) {
                text = text.replace(Command.REVERSE.command, "").trim();
                sendReverse(text);
                wasCommand = true;
            } else if (text.startsWith(Command.CREATE_ROOM.command)) {
                text = text.replace(Command.CREATE_ROOM.command, "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE.warning(TextFX.colorize("This command requires a room name as an argument", Color.RED));
                    return true;
                }
                sendRoomAction(text, RoomAction.CREATE);
                wasCommand = true;
            } else if (text.startsWith(Command.JOIN_ROOM.command)) {
                text = text.replace(Command.JOIN_ROOM.command, "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE.warning(TextFX.colorize("This command requires a room name as an argument", Color.RED));
                    return true;
                }
                sendRoomAction(text, RoomAction.JOIN);
                wasCommand = true;
            } else if (text.startsWith(Command.LEAVE_ROOM.command) || text.startsWith("leave")) {
                // Note: Accounts for /leave and /leaveroom variants (or anything beginning with
                // /leave)
                sendRoomAction(text, RoomAction.LEAVE);
                wasCommand = true;
            } else if (text.startsWith(Command.LIST_ROOMS.command)) {
                text = text.replace(Command.LIST_ROOMS.command, "").trim();

                sendRoomAction(text, RoomAction.LIST);
                wasCommand = true;
            }
            
            // UCID: mramos2001
            // Date: 07/21/2025
            // Description: Add support for /pick command and CHOICE payload to Client.java
            else if (text.startsWith(Command.PICK.command)) {
                text = text.replace(Command.PICK.command, "").trim();

                if (!text.equalsIgnoreCase("rock") && !text.equalsIgnoreCase("paper") && !text.equalsIgnoreCase("scissors")) {
                    LoggerUtil.INSTANCE.warning(TextFX.colorize("Invalid choice. Use /pick rock, paper, or scissors.", Color.RED));
                    return true;
                }

                Payload payload = new Payload();
                payload.setPayloadType(PayloadType.CHOICE);
                payload.setChoice(text.toLowerCase());
                sendToServer(payload);

                wasCommand = true;
            }
            
            else if (text.startsWith(Command.START.command)) {
                Payload payload = new Payload();
                payload.setPayloadType(PayloadType.START); // add START type
                sendToServer(payload);
                wasCommand = true;
            }


        }
        return wasCommand;
    }

    // Start Send*() methods

    /**
     * Sends a room action to the server
     * 
     * @param roomName
     * @param roomAction (join, leave, create)
     * @throws IOException
     */
    private void sendRoomAction(String roomName, RoomAction roomAction) throws IOException {
    Payload payload = new Payload();
    payload.setMessage(roomName);
    switch (roomAction) {
        case CREATE:
            payload.setPayloadType(PayloadType.ROOM_CREATE);
            break;
        case JOIN:
            payload.setPayloadType(PayloadType.ROOM_JOIN);
            break;
        case LEAVE:
            payload.setPayloadType(PayloadType.ROOM_LEAVE);
            break;
        case LIST:
            payload.setPayloadType(PayloadType.ROOM_LIST);
            break;
        default:
            LoggerUtil.INSTANCE.warning(TextFX.colorize("Invalid room action", Color.RED));
            break;
    }
    sendToServer(payload);
}


    /**
     * Sends a reverse message action to the server
     * 
     * @param message
     * @throws IOException
     */
    private void sendReverse(String message) throws IOException {
        Payload payload = new Payload();
        payload.setMessage(message);
        payload.setPayloadType(PayloadType.REVERSE);
        sendToServer(payload);

    }

    /**
     * Sends a disconnect action to the server
     * 
     * @throws IOException
     */
    private void sendDisconnect() throws IOException {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.DISCONNECT);
        payload.setClientId(myUser.getClientId());
        sendToServer(payload);
    }


    /**
     * Sends a message to the server
     * 
     * @param message
     * @throws IOException
     */
    private void sendMessage(String message) throws IOException {
        Payload payload = new Payload();
        payload.setMessage(message);
        payload.setPayloadType(PayloadType.MESSAGE);
        sendToServer(payload);
    }

    /**
     * Sends the client's name to the server (what the user desires to be called)
     * 
     * @param name
     * @throws IOException
     */
    private void sendClientName(String name) throws IOException {
        ConnectionPayload payload = new ConnectionPayload();
        payload.setClientName(name);
        payload.setPayloadType(PayloadType.CLIENT_CONNECT);
        sendToServer(payload);
    }

    public void sendToServer(Payload payload) throws IOException {
        if (isConnected()) {
            out.writeObject(payload);
            out.flush(); // good practice to ensure data is written out immediately
        } else {
            LoggerUtil.INSTANCE.warning(
                    "Not connected to server (hint: type `/connect host:port` without the quotes and replace host/port with the necessary info)");
        }
    }
    // End Send*() methods

    public void start() throws IOException {
        LoggerUtil.INSTANCE.info("Client starting");

        // Use CompletableFuture to run listenToInput() in a separate thread
        CompletableFuture<Void> inputFuture = CompletableFuture.runAsync(this::listenToInput);

        // Wait for inputFuture to complete to ensure proper termination
        inputFuture.join();
    }

    /**
     * Listens for messages from the server
     */
    private void listenToServer() {
        LoggerUtil.INSTANCE.info("Listening to server...");

        try {
            while (isRunning && isConnected()) {
                try {
                    Object obj = in.readObject(); // blocking call

                    if (obj instanceof Payload payload) {
                        processPayload(payload);
                    } else {
                        LoggerUtil.INSTANCE.warning("Received unknown object type: " + (obj != null ? obj.getClass().getName() : "null"));
                    }

                } catch (java.io.EOFException eof) {
                    LoggerUtil.INSTANCE.warning("Server closed connection (EOF).");
                    break; // clean exit on server close
                } catch (java.io.StreamCorruptedException sce) {
                    LoggerUtil.INSTANCE.warning("Stream corrupted or closed: " + sce.getMessage());
                    // Maybe pause and retry or just break, depending on your protocol
                    break;
                } catch (ClassNotFoundException | ClassCastException e) {
                    LoggerUtil.INSTANCE.severe("Invalid object received from server:", e);
                    break;
                } catch (IOException io) {
                    LoggerUtil.INSTANCE.warning("Connection dropped or failed during read: " + io.getMessage());
                    // For transient network glitches, try small pause and continue once or twice before breaking
                    // For example:
                    try {
                        Thread.sleep(500); // short pause
                    } catch (InterruptedException ignored) {}

                    // Optionally count retries, if exceeded break:
                    // break;
                }
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Fatal error in listenToServer():", e);
        } finally {
            closeServerConnection();
            LoggerUtil.INSTANCE.info("listenToServer thread stopped");
        }
    }


    private void processPayload(Payload payload) {
        switch (payload.getPayloadType()) {
            case CLIENT_CONNECT:
                break;

            case CLIENT_ID:
                processClientData(payload);
                break;

            case DISCONNECT:
                processDisconnect(payload);
                break;

        // UCID: mramos2001
        // Date: 08/05/2025
        // Description: Displays game-related messages like picks and round info
            case MESSAGE:
                LoggerUtil.INSTANCE.info(TextFX.colorize(payload.getMessage(), TextFX.Color.BLUE));

                SwingUtilities.invokeLater(() -> {
                    GameAreaUI gameArea = GameAreaUI.getInstanceIfExists();
                    if (gameArea != null) {
                        // Always log the message to the panel
                        gameArea.logEvent(payload.getMessage());

                        // Handle game over UI if needed
                        if (payload.getMessage().toLowerCase().contains("game over")) {
                            gameArea.disableButtons();
                            gameArea.stopTimer();
                            gameArea.showGameOver();
                            isFirstRoundOfGame = true;
                        }
                    }
                });
                break;


            case REVERSE:
                processReverse(payload);
                break;

            case ROOM_CREATE:
            case ROOM_JOIN:
            case ROOM_LEAVE:
            case SYNC_CLIENT:
                processRoomAction(payload);
                break;

            case ROOM_LIST:
                processRoomsList(payload);
                break;

            case CHOICE:
                processChoice(payload);
                break;

            case READY:
                updateReadyStatus(payload);
                break;

            case READY_STATUS:
                long id = payload.getClientId();
                String msg = payload.getMessage();

                boolean isReadyTemp = msg.equalsIgnoreCase("READY");

                User targetUser = knownClients.get(id);
                if (targetUser == null) {
                    targetUser = new User();
                    targetUser.setClientId(id);
                    targetUser.setClientName(getClientNameById(id));
                    knownClients.put(id, targetUser);
                }

                if ("SPECTATOR".equalsIgnoreCase(msg)) {
                    targetUser.setSpectator(true);
                    isReadyTemp = false;
                } else {
                    targetUser.setSpectator(false);
                }

                final boolean isReadyFinal = isReadyTemp;

                ReadyCheckUI.updateStatusForUser(id, isReadyFinal);

                String name = getClientNameById(id);
                SwingUtilities.invokeLater(() -> {
                    GameAreaUI ui = GameAreaUI.getInstanceIfExists();
                    if (ui != null) {
                        ui.updateReadyStatus(id, isReadyFinal, name);

                        List<UserStatus> userStatuses = knownClients.values().stream()
                            .map(user -> {
                                UserStatus status = new UserStatus(
                                    user.getClientName(),
                                    user.getClientId(),
                                    user.getPoints()    
                                );
                                status.setSpectator(user.isSpectator());
                                status.setAway(user.isAway());
                                return status;
                            })
                            .toList();

                        Set<Long> eliminatedIds = Set.of();
                        Set<Long> pendingIds = Set.of();

                        GameAreaUI.updateUserPanel(userStatuses, eliminatedIds, pendingIds);
                    }
                });
                break;





case START:
    Point location = null;

    if (ReadyCheckUI.instance != null) {
        try {
            location = ReadyCheckUI.instance.getLocationOnScreen();
        } catch (IllegalComponentStateException ignored) {}
        ReadyCheckUI.instance.dispose();
    }

    final Point finalLocation = location;

    SwingUtilities.invokeLater(() -> {
        GameAreaUI ui = GameAreaUI.getInstance(finalLocation);

        if (ui != null) {
            if (isFirstRoundOfGame) {
                ui.getGameEventsPanel().clearEvents();
                isFirstRoundOfGame = false;
            }

            ui.setVisible(true);
            ui.setAlwaysOnTop(true);
            ui.toFront();
            ui.requestFocus();
            ui.setAlwaysOnTop(false);

            User self = Client.INSTANCE.getKnownClients().get(Client.INSTANCE.getClientId());
            if (self != null && self.isSpectator()) {
                ui.setSpectatorMode(true);
            }

            ui.setGameMode(pendingGameMode);
            ui.setChoiceCooldownEnabled(pendingCooldown);

            int roundTime = 15;
            try {
                roundTime = Integer.parseInt(payload.getMessage());
            } catch (NumberFormatException ignored) {}

            ui.resetForNewRound(roundTime);

            if (pendingPointsPayload != null) {
                ui.updateUserPanel(
                    pendingPointsPayload.getUserStatuses(),
                    pendingPointsPayload.getEliminatedIds(),
                    pendingPointsPayload.getPendingPickIds()
                );
                pendingPointsPayload = null;
            }
        }
    });
    break;




            case POINTS:
                PointsPayload points = (PointsPayload) payload;
                if (GameAreaUI.getInstanceIfExists() == null) {
                    pendingPointsPayload = points;
                } else {
                    SwingUtilities.invokeLater(() -> {
                        GameAreaUI.updateUserPanel(
                            points.getUserStatuses(),
                            points.getEliminatedIds(),
                            points.getPendingPickIds()
                        );
                    });
                }
                break;

            case POSTGAME_COUNTDOWN_TICK:
                String tick = payload.getMessage();
                SwingUtilities.invokeLater(() -> {
                    GameAreaUI ui = GameAreaUI.getInstanceIfExists();
                    if (ui != null) {
                        ui.updatePostGameCountdownLabel(tick);
                    }
                });
                break;

            case READY_STATUS_REMOVE:
                long idToRemove = payload.getClientId();
                if (GameAreaUI.getInstanceIfExists() != null) {
                    GameAreaUI.getInstance().removeReadyStatus(idToRemove);
                }
                break;
            case ROOM_CLOSING:
                SwingUtilities.invokeLater(() -> {
                    GameAreaUI ui = GameAreaUI.getInstanceIfExists();

                    if (ui != null && ui.isVisible()) {
                        ui.stopTimer();
                        ui.stopPostGameCountdown();

                        int result = JOptionPane.showOptionDialog(
                            ui,
                            payload.getMessage(),
                            "Room Closing",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.INFORMATION_MESSAGE,
                            null,
                            new String[] { "Return to Lobby" },
                            "Return to Lobby"
                        );

                        if (result == 0) {
                            ui.kickToLobby();
                        }
                    }
                });
                break;
            case EXTRA_OPTIONS:
                Client.INSTANCE.setExtraOptionMode(payload.getMessage());
                break;
            case GAME_SETTINGS:
                if (payload instanceof GameSettingsPayload settings) {
                    pendingGameMode = settings.getGameMode();
                    pendingCooldown = settings.isChoiceCooldown();

                    // Update GameAreaUI if it's already open
                    GameAreaUI gameArea = GameAreaUI.getInstanceIfExists();
                    if (gameArea != null) {
                        gameArea.setGameMode(pendingGameMode);
                        gameArea.setChoiceCooldownEnabled(pendingCooldown);
                    }

                    // Update ReadyCheckUI if it's open
                    ReadyCheckUI ui = ReadyCheckUI.getInstanceIfExists();
                    if (ui != null) {
                        ui.updateRoomSettingsDisplay(pendingGameMode, pendingCooldown);
                    }
                }
                break;

            case AWAY_STATUS:
                String[] parts = payload.getMessage().split(";");
                long userId = Long.parseLong(parts[0]);
                boolean isNowAway = Boolean.parseBoolean(parts[1]);

                setUserAway(userId, isNowAway);

                User u = Client.INSTANCE.getKnownClients().get(userId);
                if (u != null) {
                    u.setAway(isNowAway);
                }

                GameAreaUI ui = GameAreaUI.getInstanceIfExists();
                if (ui != null) {
                    ui.getUserListPanel().setAwayStatus(userId, isNowAway);

                    User fallbackUser = new User();
                    fallbackUser.setClientId(userId);
                    fallbackUser.setClientName("User#" + userId);

                    String awayUserName = Client.INSTANCE.getKnownClients()
                        .getOrDefault(userId, fallbackUser)
                        .getClientName();

                    ui.logEvent(awayUserName + (isNowAway ? " is away." : " is no longer away."));
                }
                break;

            case SPECTATOR:
                User self = Client.INSTANCE.getKnownClients().get(Client.INSTANCE.getClientId());
                if (self != null) {
                    self.setSpectator(true);
                    SwingUtilities.invokeLater(() -> {
                        GameAreaUI Mui = GameAreaUI.getInstanceIfExists();
                        if (Mui != null) {
                            Mui.setSpectatorMode(true);
                        }
                        ReadyCheckUI readyUI = ReadyCheckUI.getInstanceIfExists();
                        if (readyUI != null) {
                            readyUI.setSpectatorMode(true);
                        }
                    });
                }
                break;

            default:
                LoggerUtil.INSTANCE.warning(TextFX.colorize("Unhandled payload type", TextFX.Color.YELLOW));
                break;
        }
    }

    private void processRoomsList(Payload payload) {
        if (!(payload instanceof RoomResultPayload)) {
            error("Invalid payload subclass for processRoomsList");
            return;
        }

        RoomResultPayload rrp = (RoomResultPayload) payload;
        List<String> rooms = rrp.getRooms();
        if (rooms == null || rooms.isEmpty()) {
            availableRooms = new ArrayList<>();
            return;
        }

        availableRooms = new ArrayList<>(rooms);

    }

    private void updateReadyStatus(Payload payload) {
        long id = payload.getClientId();
        boolean status = "ready".equalsIgnoreCase(payload.getMessage());

        // Make this accessible to ReadyCheckUI (e.g., via callback or map)
        ReadyCheckUI.updateStatusForUser(id, status);
    }

    public void setUserAway(long id, boolean isAway) {
        awayStatusMap.put(id, isAway);
    }

    public boolean isUserAway(long id) {
        return awayStatusMap.getOrDefault(id, false);
    }


    private void processClientData(Payload payload) {
        if (myUser.getClientId() != Constants.DEFAULT_CLIENT_ID) {
            LoggerUtil.INSTANCE.warning(TextFX.colorize("Client ID already set, this shouldn't happen", Color.YELLOW));

        }
        myUser.setClientId(payload.getClientId());
        myUser.setClientName(((ConnectionPayload) payload).getClientName());// confirmation from Server
        knownClients.put(myUser.getClientId(), myUser);
        LoggerUtil.INSTANCE.info(TextFX.colorize("Connected", Color.GREEN));
    }

    private void processDisconnect(Payload payload) {
        long disconnectId = payload.getClientId();
        String message = payload.getMessage() != null ? payload.getMessage() : "You have been disconnected.";

        if (disconnectId == myUser.getClientId() || disconnectId == -1) {
            LoggerUtil.INSTANCE.info(TextFX.colorize("You disconnected (DISCONNECT payload)", Color.RED));
            knownClients.clear();
            myUser.reset();

            SwingUtilities.invokeLater(() -> {
                GameAreaUI ui = GameAreaUI.getInstanceIfExists();
                if (ui != null) {
                    ui.dispose();
                }

                JOptionPane.showMessageDialog(null, message, "Disconnected", JOptionPane.WARNING_MESSAGE);

                Client.INSTANCE.leaveRoomAndReturnToLobby();
            });
        } 
        else if (knownClients.containsKey(disconnectId)) {
            User disconnectedUser = knownClients.remove(disconnectId);
            if (disconnectedUser != null) {
                LoggerUtil.INSTANCE.info(TextFX.colorize(
                    String.format("%s disconnected", disconnectedUser.getDisplayName()), Color.RED));
            }
        } 
        else {
            LoggerUtil.INSTANCE.info(TextFX.colorize("Received DISCONNECT for unknown or broadcast clientId: " + disconnectId, Color.RED));

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, message, "Notice", JOptionPane.WARNING_MESSAGE);
                Client.INSTANCE.leaveRoomAndReturnToLobby();
            });
        }
    }

    /**
     * UCID: mramos2001
     * Date: 07/21/2025
     * Description: Handles CHOICE payload type for syncing user pick results
     */
    private void processChoice(Payload payload) {
        String message = payload.getMessage();
        System.out.println(TextFX.colorize("Choice result: " + message, Color.PURPLE));
    }

    private String getClientNameById(long clientId) {
        User user = knownClients.get(clientId);
        return user != null ? user.getClientName() : "Unknown";
    }

    public boolean isHost() {
        return isHost;
    }

    private void processRoomAction(Payload payload) {
        if (!(payload instanceof ConnectionPayload)) {
            error("Invalid payload subclass for processRoomAction");
            return;
        }

        ConnectionPayload connectionPayload = (ConnectionPayload) payload;

        if (connectionPayload.getClientId() == getClientId()) {
            this.isHost = connectionPayload.isHost();
            LoggerUtil.INSTANCE.info(TextFX.colorize(
                isHost ? "You are the host of this room." : "You are a regular participant.",
                Color.CYAN));
        }

        if (connectionPayload.getClientId() == Constants.DEFAULT_CLIENT_ID) {
            knownClients.clear();
            hasJoinedRoom = false;
            setCurrentRoomName("N/A");
            return;
        }

        switch (connectionPayload.getPayloadType()) {
            case ROOM_LEAVE:
                if (knownClients.containsKey(connectionPayload.getClientId())) {
                    knownClients.remove(connectionPayload.getClientId());
                }
                if (connectionPayload.getMessage() != null) {
                    LoggerUtil.INSTANCE.info(TextFX.colorize(connectionPayload.getMessage(), Color.YELLOW));
                }
                hasJoinedRoom = false;
                setCurrentRoomName("N/A");
                break;

case ROOM_JOIN:
    if (connectionPayload.getMessage() != null) {
        String msg = connectionPayload.getMessage();
        LoggerUtil.INSTANCE.info(TextFX.colorize(msg, Color.GREEN));

        if (msg.toLowerCase().contains("does not exist")) {
            if (roomJoinCallback != null) {
                roomJoinCallback.onFailure(msg);
            }
            return;
        }

        if (msg.toLowerCase().contains("joined room")) {
            String[] parts = msg.trim().split(" ");
            if (parts.length >= 4) {
                String roomName = parts[3];
                setCurrentRoomName(roomName);
            }
        }

        if (msg.equalsIgnoreCase("STARTED")) {
            SwingUtilities.invokeLater(() -> {
                if (ReadyCheckUI.instance != null) {
                    ReadyCheckUI.instance.dispose();
                }
                GameAreaUI ui = GameAreaUI.getInstance(null);
                ui.setVisible(true);
                ui.setSpectatorMode(true);
            });

            if (!hasJoinedRoom) {
                hasJoinedRoom = true;
                if (roomJoinCallback != null) {
                    roomJoinCallback.onSuccess();
                    roomJoinCallback = null;
                }
            }
            return;
        } 

        if (msg.equalsIgnoreCase("NOT_STARTED")) {
            SwingUtilities.invokeLater(() -> {
                if (ReadyCheckUI.instance == null) {
                    new ReadyCheckUI(null);
                } else {
                    ReadyCheckUI.instance.setVisible(true);
                }
            });

            if (!hasJoinedRoom) {
                hasJoinedRoom = true;
                if (roomJoinCallback != null) {
                    roomJoinCallback.onSuccess();
                    roomJoinCallback = null;
                }
            }
            return;
        }

        if (!hasJoinedRoom) {
            hasJoinedRoom = true;
            if (roomJoinCallback != null) {
                roomJoinCallback.onSuccess();
                roomJoinCallback = null;
            }
        }
    }
    // fall through to SYNC_CLIENT

case SYNC_CLIENT:
    if (!knownClients.containsKey(connectionPayload.getClientId())) {
        User user = new User();
        user.setClientId(connectionPayload.getClientId());
        user.setClientName(connectionPayload.getClientName());
        knownClients.put(connectionPayload.getClientId(), user);
    }
    break;



            default:
                error("Invalid payload type for processRoomAction");
                break;
        }
    }



    private void processMessage(Payload payload) {
        LoggerUtil.INSTANCE.info(TextFX.colorize(payload.getMessage(), Color.BLUE));
    }

    private void processReverse(Payload payload) {
        LoggerUtil.INSTANCE.info(TextFX.colorize(payload.getMessage(), Color.PURPLE));
    }
    // End process*() methods

    /**
     * Listens for keyboard input from the user
     */
    private void listenToInput() {
        try (Scanner si = new Scanner(System.in)) {
            LoggerUtil.INSTANCE.info("Waiting for input");
            while (isRunning) {
                String userInput = si.nextLine();
                if (!processClientCommand(userInput)) {
                    sendMessage(userInput);
                }
            }
        } catch (IOException ioException) {
            LoggerUtil.INSTANCE.severe("Error in listentToInput()",ioException);
            //ioException.printStackTrace();
        }
        LoggerUtil.INSTANCE.info("listenToInput thread stopped");
    }

    /**
     * Closes the client connection and associated resources
     */
    private void close() {
        isRunning = false;
        closeServerConnection();
        LoggerUtil.INSTANCE.info("Client terminated");
        // System.exit(0); // Terminate the application
    }

    /**
     * Closes the server connection and associated resources
     */
    private void closeServerConnection() {
        try {
            if (out != null) {
                LoggerUtil.INSTANCE.info("Closing output stream");
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (in != null) {
                LoggerUtil.INSTANCE.info("Closing input stream");
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (server != null) {
                LoggerUtil.INSTANCE.info("Closing connection");
                server.close();
                LoggerUtil.INSTANCE.info("Closed Socket");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = Client.INSTANCE;
        try {
            client.start();
        } catch (IOException e) {
            System.out.println("Exception from main()");
            e.printStackTrace();
        }
    }

    public ConcurrentHashMap<Long, User> getKnownClients() {
        return knownClients;
    }

    public interface RoomJoinCallback {
        void onSuccess();
        void onFailure(String message);
    }


    private List<String> availableRooms = new ArrayList<>();

    public List<String> getAvailableRooms() {
        return availableRooms;
    }

    public String getClientName() {
        return myUser.getClientName();
    }

    public long getClientId() {
        return myUser.getClientId();
    }

    private String currentRoomName = "N/A";

    public String getCurrentRoomName() {
        return currentRoomName;
    }

    public void setCurrentRoomName(String name) {
        this.currentRoomName = name;
    }

    // UCID: mramos2001
    // Date: 08/05/2025
    // Description: Sends CHOICE payload to server with player's move

    public void sendChoice(String choice) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.CHOICE);
        payload.setChoice(choice);
        Client.INSTANCE.sendPayload(payload);

    }

    public void sendPayload(Payload payload) {
        try {
            if (isConnected()) {
                out.writeObject(payload);
                out.flush();
            } else {
                LoggerUtil.INSTANCE.warning("Attempted to send payload while disconnected.");
            }
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Failed to send payload:", e);
        }
    }


    public void sendReadyStatus(boolean ready) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.READY);
        payload.setMessage(ready ? "ready" : "not ready");
        sendPayload(payload);
    }

    public void leaveRoomAndReturnToLobby(Point location) {
        SwingUtilities.invokeLater(() -> {
            LoginRoomUI loginUI = new LoginRoomUI(location);
            loginUI.setLocationByPlatform(false);
            if (location != null) {
                loginUI.setLocation(location);
            } else {
                loginUI.setLocationRelativeTo(null);
            }
            loginUI.setVisible(true);
        });
    }

    public void leaveRoomAndReturnToLobby() {
        leaveRoomAndReturnToLobby(null);
    }

    public void sendRoomLeave() {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.ROOM_LEAVE);
        sendPayload(payload);
    }

    public long getRoomCreatorId() {
        return currentRoom != null ? currentRoom.getCreatorId() : -1;
    }

    public void setExtraOptionMode(String mode) {
        this.extraOptionMode = mode;
    }

    public boolean isExtraChoicesAllowed() {
        return "RPS5 - Always".equalsIgnoreCase(extraOptionMode) || 
            ("RPS5 - At 3 Players".equalsIgnoreCase(extraOptionMode) && getKnownClients().size() <= 3);
    }

    public String getExtraOptionMode() {
        return extraOptionMode;
    }

    public void sendSetGameMode(String mode) {
        GameSettingsPayload payload = new GameSettingsPayload();
        payload.setGameMode(mode);
        sendPayload(payload);
    }

    public boolean isAway() {
        GameAreaUI ui = GameAreaUI.getInstanceIfExists();
        return ui != null && ui.isAway;
    }


}