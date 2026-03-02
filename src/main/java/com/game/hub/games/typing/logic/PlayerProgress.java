package com.game.hub.games.typing.logic;

import java.util.Comparator;

public class PlayerProgress {
    private String typed = "";
    private double accuracy = 0;
    private boolean finished = false;
    private long finishedAtEpochMs = 0;

    public String getTyped() { return typed; }
    public void setTyped(String typed) { this.typed = typed; }
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public long getFinishedAtEpochMs() { return finishedAtEpochMs; }
    public void setFinishedAtEpochMs(long finishedAtEpochMs) { this.finishedAtEpochMs = finishedAtEpochMs; }

    public void resetForRace() {
        typed = "";
        accuracy = 0;
        finished = false;
        finishedAtEpochMs = 0;
    }

    public static Comparator<PlayerProgress> accuracyComparator() {
        return Comparator.comparingDouble(PlayerProgress::getAccuracy);
    }
}
