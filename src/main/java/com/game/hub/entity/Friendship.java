package com.game.hub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requesterId;
    private String addresseeId;
    private boolean accepted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    public String getAddresseeId() { return addresseeId; }
    public void setAddresseeId(String addresseeId) { this.addresseeId = addresseeId; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}