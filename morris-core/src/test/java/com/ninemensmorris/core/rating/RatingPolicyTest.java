package com.ninemensmorris.core.rating;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RatingPolicyTest {

    private static final boolean NO_GUEST = false;
    private static final boolean WITH_GUEST = true;

    @Nested
    class 반복_대전_감쇠 {

        @Test
        @DisplayName("처음 두 판은 그대로 반영한다")
        void 처음_두판은_온전히_반영한다() {
            // then
            assertThat(RatingPolicy.repeatOpponentFactor(0)).isEqualTo(1.0);
            assertThat(RatingPolicy.repeatOpponentFactor(1)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("같은 상대와 반복할수록 변동 폭이 줄어든다")
        void 반복할수록_줄어든다() {
            // then
            assertThat(RatingPolicy.repeatOpponentFactor(2)).isEqualTo(0.5);
            assertThat(RatingPolicy.repeatOpponentFactor(4)).isEqualTo(0.25);
        }

        @Test
        @DisplayName("일정 횟수를 넘으면 아예 변동하지 않는다")
        void 일정_횟수를_넘으면_변동하지_않는다() {
            // then
            assertThat(RatingPolicy.repeatOpponentFactor(6)).isZero();
            assertThat(RatingPolicy.repeatOpponentFactor(100)).isZero();
        }

        @Test
        @DisplayName("감쇠해도 부호는 유지된다")
        void 감쇠해도_부호는_유지된다() {
            // when — 1 * 0.25 는 반올림하면 0 이 되지만 승패가 사라지면 안 된다
            int gained = RatingPolicy.applyFactor(1, 0.25);
            int lost = RatingPolicy.applyFactor(-1, 0.25);

            // then
            assertThat(gained).isEqualTo(1);
            assertThat(lost).isEqualTo(-1);
        }

        @Test
        @DisplayName("계수가 0이면 변동이 없다")
        void 계수가_0이면_변동이_없다() {
            // then
            assertThat(RatingPolicy.applyFactor(32, 0.0)).isZero();
            assertThat(RatingPolicy.applyFactor(-32, 0.0)).isZero();
        }

        @Test
        @DisplayName("계수를 곱한 값으로 줄어든다")
        void 계수만큼_줄어든다() {
            // then
            assertThat(RatingPolicy.applyFactor(32, 0.5)).isEqualTo(16);
            assertThat(RatingPolicy.applyFactor(-32, 0.25)).isEqualTo(-8);
        }
    }

    @Nested
    class 랭크전_여부 {

        @Test
        @DisplayName("너무 일찍 끝난 판은 반영하지 않는다")
        void 조기_종료는_반영하지_않는다() {
            // then — 붙자마자 기권을 반복하는 파밍을 막는다
            assertThat(RatingPolicy.isRated(0, NO_GUEST)).isFalse();
            assertThat(RatingPolicy.isRated(RatingPolicy.MIN_RATED_MOVES - 1, NO_GUEST))
                    .isFalse();
        }

        @Test
        @DisplayName("최소 수를 채우면 반영한다")
        void 최소_수를_채우면_반영한다() {
            // then
            assertThat(RatingPolicy.isRated(RatingPolicy.MIN_RATED_MOVES, NO_GUEST))
                    .isTrue();
        }

        @Test
        @DisplayName("게스트가 끼면 판 길이와 무관하게 반영하지 않는다")
        void 게스트가_끼면_반영하지_않는다() {
            // then — 게스트는 무한히 만들 수 있어 랭크전이 될 수 없다
            assertThat(RatingPolicy.isRated(100, WITH_GUEST)).isFalse();
        }
    }
}
