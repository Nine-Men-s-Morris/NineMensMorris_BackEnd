package com.ninemensmorris.core.rating;

// MMR 구간을 등급으로 바꾼 것
// 1237 같은 숫자보다 등급이 읽기 쉽고 목표가 생김
// 화면에 보일 한글 표기는 프론트에서 매핑함
//
// 구간 폭이 위로 갈수록 넓어진다 (200 / 250 / 300 / 350)
// Elo 는 400점 차이가 기대 승률 10:1 을 뜻하므로
// 같은 폭을 유지하면 상위 등급이 실제보다 쉽게 나온다
public enum Tier {
    UNRANKED(Integer.MIN_VALUE),
    BRONZE(Integer.MIN_VALUE),
    SILVER(900),
    GOLD(1100),
    PLATINUM(1350),
    DIAMOND(1650),
    MASTER(2000);

    private final int floor;

    Tier(int floor) {
        this.floor = floor;
    }

    // 배치 판수를 채우기 전에는 등급을 매기지 않음
    public static Tier of(int mmr, int gamesPlayed) {
        if (gamesPlayed < Rating.PLACEMENT_GAMES) {
            return UNRANKED;
        }
        Tier matched = BRONZE;
        for (Tier tier : values()) {
            if (tier != UNRANKED && mmr >= tier.floor) {
                matched = tier;
            }
        }
        return matched;
    }

    public int floor() {
        return floor;
    }
}
