package Project.Common;

public class ConnectionPayload extends Payload {
    private String clientName;
    private boolean isHost; // ✅ New field to track host status

    /**
     * @return the clientName
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * @param clientName the clientName to set
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /**
     * @return true if this client is the host
     */
    public boolean isHost() {
        return isHost;
    }

    /**
     * @param isHost whether this client is the host
     */
    public void setHost(boolean isHost) {
        this.isHost = isHost;
    }

    @Override
    public String toString() {
        return super.toString() +
                String.format(" ClientName: [%s], IsHost: [%s]",
                        getClientName(), isHost);
    }
}
