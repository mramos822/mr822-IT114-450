// UCID: mramos2001
// Date: 07/21/2025
// Description: Extends Payload to include points for syncing player scores,
// and tracks eliminated and pending users for UI display

package Project.Common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PointsPayload extends Payload {
    private List<UserStatus> userStatuses = new ArrayList<>();
    private Set<Long> eliminatedIds = new HashSet<>();
    private Set<Long> pendingPickIds = new HashSet<>();
    private List<String> battleMessages = new ArrayList<>();


    public PointsPayload() {
        setPayloadType(PayloadType.POINTS);
    }

    public List<UserStatus> getUserStatuses() {
        return userStatuses;
    }

    public void setUserStatuses(List<UserStatus> userStatuses) {
        this.userStatuses = userStatuses;
    }

    public void addUserStatus(UserStatus status) {
        this.userStatuses.add(status);
    }

    public Set<Long> getEliminatedIds() {
        return eliminatedIds;
    }

    public void setEliminatedIds(Set<Long> eliminatedIds) {
        this.eliminatedIds = eliminatedIds;
    }

    public Set<Long> getPendingPickIds() {
        return pendingPickIds;
    }

    public void setPendingPickIds(Set<Long> pendingPickIds) {
        this.pendingPickIds = pendingPickIds;
    }

    public List<String> getBattleMessages() {
        return battleMessages;
    }

    public void addBattleMessage(String msg) {
        battleMessages.add(msg);
    }

}
