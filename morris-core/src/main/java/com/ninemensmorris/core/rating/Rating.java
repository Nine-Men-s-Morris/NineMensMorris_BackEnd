package com.ninemensmorris.core.rating;

// Elo 기반 MMR
//
// 제로섬이라 한쪽이 얻는 만큼 상대가 잃고 전체 합이 보존됨
// 기존 방식(승 +30 / 패 -20)은 판마다 총합이 +10 씩 늘어
// 점수가 실력이 아니라 판수를 나타냈음
public final class Rating {

    // 최하위가 아니라 중간에서 시작해야 오르내릴 여지가 생김 (SILVER 구간)
    public static final int INITIAL = 1000;

    // 음수 방지 하한
    public static final int FLOOR = 100;

    // 이 판수를 채울 때까지는 크게 움직여 제자리를 빨리 찾게 함
    public static final int PLACEMENT_GAMES = 10;

    private static final int K_PLACEMENT = 48;
    private static final int K_DEFAULT = 32;

    private Rating() {}

    // actualScore 는 승 1.0, 무 0.5, 패 0.0
    public static int delta(int myMmr, int opponentMmr, double actualScore, int myGamesPlayed) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentMmr - myMmr) / 400.0));
        int k = myGamesPlayed < PLACEMENT_GAMES ? K_PLACEMENT : K_DEFAULT;
        return (int) Math.round(k * (actualScore - expected));
    }

    public static int win(int myMmr, int opponentMmr, int myGamesPlayed) {
        return delta(myMmr, opponentMmr, 1.0, myGamesPlayed);
    }

    public static int loss(int myMmr, int opponentMmr, int myGamesPlayed) {
        return delta(myMmr, opponentMmr, 0.0, myGamesPlayed);
    }

    public static int draw(int myMmr, int opponentMmr, int myGamesPlayed) {
        return delta(myMmr, opponentMmr, 0.5, myGamesPlayed);
    }

    public static int applyFloor(int mmr) {
        return Math.max(FLOOR, mmr);
    }
}
