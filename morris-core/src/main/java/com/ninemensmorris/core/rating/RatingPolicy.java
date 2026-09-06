package com.ninemensmorris.core.rating;

// 레이팅 어뷰징 대책
//
// Elo 는 제로섬이라 한 계정을 희생시켜 다른 계정을 올릴 수 있음
// 짜고 치는 대전과 즉시 기권 파밍을 막는 계수를 여기서 정함
public final class RatingPolicy {

    // 이보다 적게 두고 끝난 판은 승부가 갈린 것으로 보지 않음
    // 붙자마자 기권을 반복해 상대 MMR 을 올려주는 파밍을 막음
    public static final int MIN_RATED_MOVES = 10;

    // 같은 상대와 이 시간 안에 둔 판을 반복 대전으로 셈
    public static final int REPEAT_WINDOW_HOURS = 24;

    private RatingPolicy() {}

    // 같은 상대와 최근에 몇 판을 뒀는지에 따라 변동 폭을 줄임
    // playedBefore 는 이번 판을 제외한 횟수
    public static double repeatOpponentFactor(int playedBefore) {
        if (playedBefore < 2) {
            return 1.0;
        }
        if (playedBefore < 4) {
            return 0.5;
        }
        if (playedBefore < 6) {
            return 0.25;
        }
        return 0.0;
    }

    // 레이팅에 반영할 판인지
    // guestInvolved 는 게스트가 낀 경우. 게스트는 무한히 만들 수 있어 랭크전이 될 수 없음
    public static boolean isRated(int totalMoves, boolean guestInvolved) {
        return !guestInvolved && totalMoves >= MIN_RATED_MOVES;
    }

    // 감쇠를 적용한 최종 변동값
    // 0 으로 반올림되지 않도록 부호를 유지함
    public static int applyFactor(int delta, double factor) {
        if (factor == 0.0 || delta == 0) {
            return 0;
        }
        int scaled = (int) Math.round(delta * factor);
        if (scaled != 0) {
            return scaled;
        }
        return delta > 0 ? 1 : -1;
    }
}
