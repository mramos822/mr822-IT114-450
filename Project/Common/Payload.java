package Project.Common;

import java.io.Serializable;
import java.util.List;

public class Payload implements Serializable {
    private PayloadType payloadType;
    private long clientId;
    private String message;
    private String choice;

    private List<UserStatus> userStatusList;


    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getChoice() {
        return choice;
    }

    public void setChoice(String choice) {
        this.choice = choice;
    }

    // Add getter and setter for userStatusList
    public List<UserStatus> getUserStatusList() {
        return userStatusList;
    }

    public void setUserStatusList(List<UserStatus> userStatusList) {
        this.userStatusList = userStatusList;
    }

    @Override
    public String toString() {
        return String.format("Payload[%s] Client Id [%s] Message: [%s]", getPayloadType(), getClientId(), getMessage());
    }
}
