package com.ninemensmorris.game.domain;

public class MorrisStatus {

    public enum Status {
        WAITING, PLAYING, FINISHED
    }

    private Status currentStatus;

    public MorrisStatus() {
        this.currentStatus = Status.WAITING;
    }

    public void setStatus(Status status) {
        this.currentStatus = status;
    }

    public Status getStatus() {
        return this.currentStatus;
    }

    public void printStatus() {
        switch (this.currentStatus) {
            case PLAYING:
                System.out.println("게임이 진행 중입니다.");
                break;
            case FINISHED:
                System.out.println("게임이 종료되었습니다.");
                break;
        }
    }
}
