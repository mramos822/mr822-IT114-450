package Project.Common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoomResultPayload extends Payload {
    private List<String> rooms = new ArrayList<>();
    private long creatorId;
    private String roomName;

    public RoomResultPayload() {
        setPayloadType(PayloadType.ROOM_LIST);
    }

    public List<String> getRooms() {
        return rooms;
    }

    public void setRooms(List<String> rooms) {
        this.rooms = rooms;
    }

    public void setRoomsWithCounts(Map<String, ? extends RoomSummary> roomMap) {
        rooms.clear();
        for (Map.Entry<String, ? extends RoomSummary> entry : roomMap.entrySet()) {
            String roomName = entry.getKey();
            int userCount = entry.getValue().getClientCount();
            if (userCount == 1) {
                rooms.add(roomName + " (" + userCount + " user)");
            } else {
                rooms.add(roomName + " (" + userCount + " users)");
            }
        }
    }

    @Override
    public String toString() {
        return super.toString() + "Rooms [" + String.join(",", rooms) + "]";
    }

    public long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(long creatorId) {
        this.creatorId = creatorId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

}
