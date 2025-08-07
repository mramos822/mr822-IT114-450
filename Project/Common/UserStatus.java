// UCID: mramos2001
// Date: 2025-08-01
// Description: Transferable status object for each player including name, ID, and score.

package Project.Common;

import java.io.Serializable;

public class UserStatus implements Serializable {
    private String name;
    private long id;
    private int points;

    public UserStatus(String name, long id, int points) {
        this.name = name;
        this.id = id;
        this.points = points;
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public int getPoints() {
        return points;
    }

    private boolean away;

    public boolean isAway() {
        return away;
    }

    public void setAway(boolean away) {
        this.away = away;
    }

    private boolean spectator;

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }


}
