package com.ninemensmorris.game.domain;

public class MorrisStatus {

    public enum Status {
        RUNNING, ENDED
    }

    private Status currentStatus;

    public MorrisStatus() {
        this.currentStatus = Status.RUNNING;
    }

    public void setStatus(Status status) {
        this.currentStatus = status;
    }

    public Status getStatus() {
        return this.currentStatus;
    }

    public void printStatus() {
        switch (this.currentStatus) {
            case RUNNING:
                System.out.println("게임이 진행 중입니다.");
                break;
            case ENDED:
                System.out.println("게임이 종료되었습니다.");
                break;
        }
    }
}
