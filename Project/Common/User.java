package Project.Common;

public class User {
    private long clientId = Constants.DEFAULT_CLIENT_ID;
    private String clientName;
    private int points = 0;

    public void addPoints(int pts) {
        this.points += pts;
    }


    public int getPoints() {
        return points;
    }

    public void resetPoints() {
        points = 0;
    }

    /**
     * @return the clientId
     */
    public long getClientId() {
        return clientId;
    }

    /**
     * @param clientId the clientId to set
     */
    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    /**
     * @return the username
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * @param username the username to set
     */
    public void setClientName(String username) {
        this.clientName = username;
    }

    public String getDisplayName() {
        return String.format("%s#%s", this.clientName, this.clientId);
    }

    public void reset() {
        this.clientId = Constants.DEFAULT_CLIENT_ID;
        this.clientName = null;
        this.points = 0;
    }

    private boolean isAway = false;

    public boolean isAway() {
        return isAway;
    }

    public void setAway(boolean away) {
        this.isAway = away;
    }

    private boolean spectator;

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

}
