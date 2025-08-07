package Project.Server;

import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.GameSettingsPayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomAction;
import Project.Common.RoomResultPayload;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;
import Project.Common.User;
import Project.Exceptions.RoomNotFoundException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A server-side representation of a single client
 */
public class ServerThread extends BaseServerThread {
    private Consumer<ServerThread> onInitializationComplete; // callback to inform when this object is ready
    private User user = new User(); // Added user field

    /**
     * A wrapper method so we don't need to keep typing out the long/complex sysout
     * line inside
     * 
     * @param message
     */
    @Override
    protected void info(String message) {
        if (!message.contains("ROOM_LIST")) { // Skip spammy logs
            LoggerUtil.INSTANCE.info(TextFX.colorize(String.format("Thread[%s]: %s", this.getClientId(), message), Color.CYAN));
        }
    }


    /**
     * Wraps the Socket connection and takes a Server reference and a callback
     * 
     * @param myClient
     * @param server
     * @param onInitializationComplete method to inform listener that this object is
     *                                 ready
     */
    protected ServerThread(Socket myClient, Consumer<ServerThread> onInitializationComplete) {
        Objects.requireNonNull(myClient, "Client socket cannot be null");
        Objects.requireNonNull(onInitializationComplete, "callback cannot be null");
        info("ServerThread created");
        // get communication channels to single client
        this.client = myClient;
        // this.clientId = this.threadId(); // An id associated with the thread
        // instance, used as a temporary identifier
        this.onInitializationComplete = onInitializationComplete;
    }

    // Getter for User
    public User getUser() {
        return user;
    }

    // Setter for User
    public void setUser(User user) {
        this.user = user;
    }

    // Start Send*() Methods
    public boolean sendRooms(List<String> rooms) {
        RoomResultPayload rrp = new RoomResultPayload();
        rrp.setRooms(rooms);
        return sendToClient(rrp);
    }

    protected boolean sendDisconnect(long clientId) {
        Payload payload = new Payload();
        payload.setClientId(clientId);
        payload.setPayloadType(PayloadType.DISCONNECT);
        return sendToClient(payload);
    }

    protected boolean sendResetUserList() {
        return sendClientInfo(Constants.DEFAULT_CLIENT_ID, null, RoomAction.JOIN);
    }

    /**
     * Syncs Client Info (id, name, join status) to the client
     * 
     * @param clientId   use -1 for reset/clear
     * @param clientName
     * @param action     RoomAction of Join or Leave
     * @return true for successful send
     */
    protected boolean sendClientInfo(long clientId, String clientName, RoomAction action) {
        return sendClientInfo(clientId, clientName, action, false);
    }

    /**
     * Syncs Client Info (id, name, join status) to the client
     * 
     * @param clientId   use -1 for reset/clear
     * @param clientName
     * @param action     RoomAction of Join or Leave
     * @param isSync     True is used to not show output on the client side (silent
     *                   sync)
     * @return true for successful send
     */
    protected boolean sendClientInfo(long clientId, String clientName, RoomAction action, boolean isSync) {
        ConnectionPayload payload = new ConnectionPayload();
        switch (action) {
            case JOIN:
                payload.setPayloadType(PayloadType.ROOM_JOIN);
                payload.setMessage("You joined room " + currentRoom.getRoomName());
                break;
            case LEAVE:
                payload.setPayloadType(PayloadType.ROOM_LEAVE);
                break;
            default:
                break;
        }
        if (isSync) {
            payload.setPayloadType(PayloadType.SYNC_CLIENT);
        }
        payload.setClientId(clientId);
        payload.setClientName(clientName);

        if (currentRoom != null && clientId == getClientId()) {
            payload.setHost(currentRoom.isHost(this));
        }

        return sendToClient(payload);

    }


    /**
     * Sends this client's id to the client.
     * This will be a successfully connection handshake
     * 
     * @return true for successful send
     */
    protected boolean sendClientId() {
        ConnectionPayload payload = new ConnectionPayload();
        payload.setPayloadType(PayloadType.CLIENT_ID);
        payload.setClientId(getClientId());
        payload.setClientName(getClientName());
                                               
        return sendToClient(payload);
    }


    // UCID: mramos2001
    // Date: 07/21/2025
    /**
     * Sends a message to the client
     * 
     * @param clientId who it's from
     * @param message
     * @return true for successful send
     */
    protected boolean sendMessage(long clientId, String message) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.MESSAGE);
        payload.setMessage(message);
        payload.setClientId(clientId);
        return sendToClient(payload);
    }

    @Override
    protected void processPayload(Payload incoming) {

        switch (incoming.getPayloadType()) {
            case CLIENT_CONNECT:
                setClientName(((ConnectionPayload) incoming).getClientName().trim());
                user.setClientId(getClientId());
                user.setClientName(getClientName());
                break;
            case DISCONNECT:
                currentRoom.handleDisconnect(this);
                break;
            case MESSAGE:
                currentRoom.handleMessage(this, incoming.getMessage());
                break;
            case REVERSE:
                currentRoom.handleReverseText(this, incoming.getMessage());
                break;
            case ROOM_CREATE:
                currentRoom.handleCreateRoom(this, incoming.getMessage());

                if (currentRoom instanceof GameRoom gameRoom) {
                    GameSettingsPayload settings = gameRoom.getCurrentGameSettingsPayload();
                    sendToClient(settings);
                }
                break;

            case ROOM_JOIN:
                try {
                    Server.INSTANCE.joinRoom(incoming.getMessage(), this);

                    if (currentRoom instanceof GameRoom gameRoom) {
                        GameSettingsPayload settings = new GameSettingsPayload();
                        settings.setGameMode(gameRoom.getGameMode());
                        settings.setChoiceCooldown(gameRoom.isChoiceCooldown());
                        settings.setAllowChoiceChanges(gameRoom.isAllowChoiceChanges());
                        sendToClient(settings);

                        ConnectionPayload roomStatusPayload = new ConnectionPayload();
                        roomStatusPayload.setPayloadType(PayloadType.ROOM_JOIN);
                        roomStatusPayload.setClientId(getClientId());
                        roomStatusPayload.setClientName(getClientName());
                        roomStatusPayload.setMessage(gameRoom.isGameStarted() ? "STARTED" : "NOT_STARTED");
                        sendToClient(roomStatusPayload);

                    } else {
                        ConnectionPayload roomStatusPayload = new ConnectionPayload();
                        roomStatusPayload.setPayloadType(PayloadType.ROOM_JOIN);
                        roomStatusPayload.setClientId(getClientId());
                        roomStatusPayload.setClientName(getClientName());
                        roomStatusPayload.setMessage("NOT_STARTED");
                        sendToClient(roomStatusPayload);
                    }

                } catch (RoomNotFoundException e) {
                    LoggerUtil.INSTANCE.warning("Room not found: " + incoming.getMessage());

                    ConnectionPayload failurePayload = new ConnectionPayload();
                    failurePayload.setPayloadType(PayloadType.ROOM_JOIN);
                    failurePayload.setClientId(getClientId());
                    failurePayload.setClientName(getClientName());
                    failurePayload.setMessage("Room \"" + incoming.getMessage() + "\" does not exist.");

                    sendToClient(failurePayload);
                }
                break;

            case ROOM_LEAVE:
                currentRoom.handleJoinRoom(this, Room.LOBBY);
                break;
            case ROOM_LIST:
                currentRoom.handleListRooms(this, incoming.getMessage());
                break;
            // UCID: mramos2001
            // Date: 07/21/2025
            // Description: Handle CHOICE payload from client
            case CHOICE:
                if (currentRoom != null) {
                    currentRoom.handleChoice(this, incoming.getChoice());
                }
                break;
            case START:
                if (currentRoom instanceof GameRoom gameRoom) {
                    gameRoom.onSessionStart();
                }
                break;
            case READY:
                if (currentRoom != null) {
                    String msg = incoming.getMessage();
                    boolean isSpectator = "spectator".equalsIgnoreCase(msg);
                    boolean isNowReady = "ready".equalsIgnoreCase(msg);

                    getUser().setSpectator(isSpectator);

                    if (currentRoom instanceof GameRoom gameRoom) {
                        gameRoom.handleReady(this, isNowReady, isSpectator);

                        if (!isSpectator && currentRoom.isHost(this)) {
                            GameSettingsPayload settings = gameRoom.getCurrentGameSettingsPayload();
                            for (ServerThread player : gameRoom.getActivePlayers()) {
                                player.sendToClient(settings);
                            }
                        }
                    } else {
                        this.setReady(isNowReady);
                        currentRoom.broadcastReadyStatus(getClientId(), isNowReady);
                    }
                }
                break;




            case EXTRA_OPTIONS:
                if (incoming instanceof GameSettingsPayload settings && currentRoom instanceof GameRoom room) {
                    room.setGameMode(
                        settings.getGameMode(),
                        settings.isChoiceCooldown(),
                        getClientName()
                    );
                }
                break;

            case GAME_SETTINGS:
                if (incoming instanceof GameSettingsPayload settings && currentRoom instanceof GameRoom room) {
                    room.setGameMode(
                        settings.getGameMode(),
                        settings.isChoiceCooldown(),
                        getClientName()
                    );

                    room.setAllowChoiceChanges(settings.isAllowChoiceChanges());

                    GameSettingsPayload broadcastPayload = new GameSettingsPayload();
                    broadcastPayload.setGameMode(settings.getGameMode());
                    broadcastPayload.setChoiceCooldown(settings.isChoiceCooldown());
                    broadcastPayload.setAllowChoiceChanges(settings.isAllowChoiceChanges());

                    for (ServerThread client : room.getActivePlayers()) {
                        client.sendToClient(broadcastPayload);
                    }
                }
                break;


                // UCID: mramos2001
                // Date: 08/06/2025
                // Description: Server receives AWAY payload, updates user status, broadcasts, and logs message.
                case AWAY_STATUS:
                    boolean nowAway = Boolean.parseBoolean(incoming.getMessage());
                    user.setAway(nowAway);

                    String awayMessage = getClientName() + (nowAway ? " is away." : " is no longer away.");
                    if (currentRoom != null) {
                        currentRoom.broadcastMessage(awayMessage);
                    }

                    Payload sync = new Payload();
                    sync.setPayloadType(PayloadType.AWAY_STATUS);
                    sync.setMessage(getClientId() + ";" + nowAway);

                    if (currentRoom instanceof GameRoom gameRoom) {
                        for (ServerThread thread : gameRoom.getActivePlayers()) {
                            thread.sendToClient(sync);
                        }
                    }
                    break;
                case SPECTATOR:
                    boolean isSpectator = Boolean.parseBoolean(incoming.getMessage());
                    getUser().setSpectator(isSpectator);

                    if (currentRoom != null) {
                        currentRoom.broadcastReadyStatus(getUser().getClientId(), false);
                    }
                    break;



            default:
                LoggerUtil.INSTANCE.warning(TextFX.colorize("Unknown payload type received", Color.RED));
                break;
        }
    }

    @Override
    protected void onInitialized() {
        onInitializationComplete.accept(this);
    }

    private boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public void sendCountdownTick(String message) {
        Payload tick = new Payload();
        tick.setPayloadType(PayloadType.POSTGAME_COUNTDOWN_TICK);
        tick.setMessage(message);
        sendToClient(tick);
    }

    public void sendKickToLobby() {
        Payload kick = new Payload();
        kick.setPayloadType(PayloadType.DISCONNECT);
        kick.setMessage("Return to Lobby");
        sendToClient(kick);
    }

    // UCID: mramos2001
    // Date: 2025-08-06
    // Description: Tracks the last ready message type sent by this client ("ready", "not_ready", "spectator")
    private String lastReadyMessage = "";

    public void setLastReadyMessage(String msg) {
        this.lastReadyMessage = msg != null ? msg : "";
    }

    public String getLastReadyMessage() {
        return lastReadyMessage;
    }

}
