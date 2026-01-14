// UCID: mramos2001
// Date: 07/21/2025
// Description: Extends Payload to include points for syncing player scores

package Project.Common;

public class PointsPayload extends Payload {
    private int points;

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    @Override
    public String toString() {
        return String.format("PointsPayload[%s] Client Id [%s] Points: [%d]",
                getPayloadType(), getClientId(), points);
    }
}
