package com.caro.game.chess.websocket;

public class ChessMoveMessage {
    private String roomId;
    private String userId;
    private Integer fromRow;
    private Integer fromCol;
    private Integer toRow;
    private Integer toCol;
    private String promotion;

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

    public Integer getFromRow() {
        return fromRow;
    }

    public void setFromRow(Integer fromRow) {
        this.fromRow = fromRow;
    }

    public Integer getFromCol() {
        return fromCol;
    }

    public void setFromCol(Integer fromCol) {
        this.fromCol = fromCol;
    }

    public Integer getToRow() {
        return toRow;
    }

    public void setToRow(Integer toRow) {
        this.toRow = toRow;
    }

    public Integer getToCol() {
        return toCol;
    }

    public void setToCol(Integer toCol) {
        this.toCol = toCol;
    }

    public String getPromotion() {
        return promotion;
    }

    public void setPromotion(String promotion) {
        this.promotion = promotion;
    }
}
