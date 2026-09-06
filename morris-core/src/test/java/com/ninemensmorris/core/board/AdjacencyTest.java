package com.ninemensmorris.core.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdjacencyTest {

    @Test
    @DisplayName("인접 관계는 대칭이다")
    void 인접_관계는_대칭이다() {
        for (int from = 0; from < Board.POINTS; from++) {
            // when
            int[] neighbours = Adjacency.of(from);

            // then
            for (int to : neighbours) {
                assertThat(Adjacency.isAdjacent(to, from))
                        .as("%d -> %d 는 있는데 %d -> %d 가 없음", from, to, to, from)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("자기 자신과 인접하지 않고 중복도 없다")
    void 자기자신과_인접하지_않는다() {
        for (int point = 0; point < Board.POINTS; point++) {
            // when
            int[] neighbours = Adjacency.of(point);

            // then
            assertThat(neighbours).doesNotContain(point);
            assertThat(Arrays.stream(neighbours).distinct().count()).isEqualTo(neighbours.length);
        }
    }

    @Test
    @DisplayName("모든 지점의 이웃은 2~4개다")
    void 이웃은_2개에서_4개다() {
        for (int point = 0; point < Board.POINTS; point++) {
            // when
            int[] neighbours = Adjacency.of(point);

            // then
            assertThat(neighbours.length).as("지점 %d", point).isBetween(2, 4);
        }
    }

    @Test
    @DisplayName("간선은 32개다 — 차수 합이 64여야 한다")
    void 간선은_32개다() {
        // when
        int degreeSum = 0;
        for (int point = 0; point < Board.POINTS; point++) {
            degreeSum += Adjacency.of(point).length;
        }

        // then
        assertThat(degreeSum).isEqualTo(64);
    }

    @Test
    @DisplayName("판을 가로지르는 연결은 없다")
    void 대각선으로는_이어지지_않는다() {
        // then
        assertThat(Adjacency.isAdjacent(0, 2)).isFalse();
        assertThat(Adjacency.isAdjacent(0, 21)).isFalse();
        assertThat(Adjacency.isAdjacent(0, 4)).isFalse();
        // 7x7 격자의 한가운데는 지점이 아니라 세로 중앙선이 위아래로 끊겨 있다
        assertThat(Adjacency.isAdjacent(7, 16)).isFalse();
    }
}
