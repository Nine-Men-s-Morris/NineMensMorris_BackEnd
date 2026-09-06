package com.ninemensmorris.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MillsTest {

    @Test
    @DisplayName("밀은 가로 8개 세로 8개로 총 16개다")
    void 밀은_16개다() {
        // when
        int[][] mills = Mills.all();

        // then
        assertThat(mills.length).isEqualTo(16);
    }

    @Test
    @DisplayName("모든 밀은 서로 다른 유효한 지점 3개로 이뤄진다")
    void 모든_밀은_유효한_지점_3개다() {
        // when
        int[][] mills = Mills.all();

        // then
        for (int[] mill : mills) {
            assertThat(mill.length).isEqualTo(3);
            assertThat(Arrays.stream(mill).boxed())
                    .as("밀 %s", Arrays.toString(mill))
                    .allMatch(point -> point >= 0 && point < Board.POINTS);
            assertThat(Arrays.stream(mill).distinct().count()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("중복된 밀이 없다")
    void 중복된_밀이_없다() {
        // given
        Set<String> seen = new HashSet<>();

        // when
        int[][] mills = Mills.all();

        // then
        for (int[] mill : mills) {
            int[] sorted = mill.clone();
            Arrays.sort(sorted);
            assertThat(seen.add(Arrays.toString(sorted)))
                    .as("중복된 밀: %s", Arrays.toString(mill))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("모든 지점은 정확히 2개의 밀에 속한다 (가로 1, 세로 1)")
    void 모든_지점은_밀_2개에_속한다() {
        for (int point = 0; point < Board.POINTS; point++) {
            // when
            int[][] mills = Mills.containing(point);

            // then
            assertThat(mills.length).as("지점 %d", point).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("containing 의 결과는 그 지점을 실제로 포함한다")
    void containing_은_해당_지점을_포함한다() {
        for (int point = 0; point < Board.POINTS; point++) {
            // when
            int[][] mills = Mills.containing(point);

            // then
            for (int[] mill : mills) {
                assertThat(mill).contains(point);
            }
        }
    }
}
