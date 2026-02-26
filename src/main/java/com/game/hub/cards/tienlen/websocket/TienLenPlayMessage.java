package com.game.hub.cards.tienlen.websocket;

import java.util.List;

public class TienLenPlayMessage {
    private String roomId;
    private String userId;
    private List<String> cardCodes;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getCardCodes() {
        return cardCodes;
    }

    public void setCardCodes(List<String> cardCodes) {
        this.cardCodes = cardCodes;
    }
}
