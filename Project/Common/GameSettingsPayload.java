package Project.Common;

public class GameSettingsPayload extends Payload {
    private String gameMode;

    public GameSettingsPayload() {
        setPayloadType(PayloadType.GAME_SETTINGS);
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    private boolean choiceCooldown;
    private boolean allowChoiceChanges;

    public boolean isChoiceCooldown() {
        return choiceCooldown;
    }

    public void setChoiceCooldown(boolean choiceCooldown) {
        this.choiceCooldown = choiceCooldown;
    }

    public boolean isAllowChoiceChanges() {
        return allowChoiceChanges;
    }

    public void setAllowChoiceChanges(boolean allowChoiceChanges) {
        this.allowChoiceChanges = allowChoiceChanges;
    }

}
