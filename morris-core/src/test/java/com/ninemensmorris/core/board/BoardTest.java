package com.ninemensmorris.core.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoardTest {

    @Nested
    class 표기법 {

        @Test
        @DisplayName("판을 그림으로 읽어 들인다")
        void 파싱한다() {
            // given
            String art =
                    """
                    B  .  .
                     . . .
                      ...
                    W.. ...
                      ...
                     . . .
                    .  .  W
                    """;

            // when
            Board board = Board.parse(art);

            // then
            assertThat(board.stoneAt(0)).isEqualTo(Stone.BLACK);
            assertThat(board.stoneAt(9)).isEqualTo(Stone.WHITE);
            assertThat(board.stoneAt(23)).isEqualTo(Stone.WHITE);
            assertThat(board.isEmpty(1)).isTrue();
            assertThat(board.count(Stone.BLACK)).isEqualTo(1);
            assertThat(board.count(Stone.WHITE)).isEqualTo(2);
        }

        @Test
        @DisplayName("읽어 들인 판을 다시 그리면 원본과 같다")
        void 왕복한다() {
            // given
            String art =
                    """
                    B  W  .
                     . B .
                      W.B
                    W.. .BW
                      ..W
                     . B .
                    .  W  .
                    """;

            // when
            String rendered = Board.parse(art).render();

            // then
            assertThat(rendered).isEqualTo(art);
        }

        @Test
        @DisplayName("줄 수가 7이 아니면 거부한다")
        void 줄_수가_틀리면_거부한다() {
            // given
            String art = "B  .  .\n . . .";

            // then
            assertThatThrownBy(() -> Board.parse(art))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("7줄");
        }
    }

    @Nested
    class 밀_판정 {

        @Test
        @DisplayName("같은 색 3개가 한 줄이면 밀이다")
        void 밀을_인식한다() {
            // given
            Board board = Board.parse(
                    """
                    B  B  B
                     . . .
                      ...
                    ... ...
                      ...
                     . . .
                    .  .  .
                    """);

            // then
            assertThat(board.isInMill(0)).isTrue();
            assertThat(board.isInMill(1)).isTrue();
            assertThat(board.isInMill(2)).isTrue();
        }

        @Test
        @DisplayName("색이 섞이면 밀이 아니다")
        void 색이_섞이면_밀이_아니다() {
            // given
            Board board = Board.parse(
                    """
                    B  B  W
                     . . .
                      ...
                    ... ...
                      ...
                     . . .
                    .  .  .
                    """);

            // then
            assertThat(board.isInMill(0)).isFalse();
        }

        @Test
        @DisplayName("빈 지점은 밀이 아니다")
        void 빈_지점은_밀이_아니다() {
            // given
            Board board = new Board();

            // then
            assertThat(board.isInMill(0)).isFalse();
        }

        @Test
        @DisplayName("세로 줄도 밀이다")
        void 세로_밀도_인식한다() {
            // given
            Board board = Board.parse(
                    """
                    B  .  .
                     . . .
                      ...
                    B.. ...
                      ...
                     . . .
                    B  .  .
                    """);

            // then
            assertThat(board.isInMill(0)).isTrue();
            assertThat(board.isInMill(9)).isTrue();
            assertThat(board.isInMill(21)).isTrue();
        }
    }

    @Nested
    class 전부_밀인지 {

        @Test
        @DisplayName("밀에 속하지 않은 돌이 하나라도 있으면 false")
        void 밀_밖의_돌이_있으면_false() {
            // given
            Board board = Board.parse(
                    """
                    B  B  B
                     . . .
                      ...
                    B.. ...
                      ...
                     . . .
                    .  .  .
                    """);

            // then
            assertThat(board.allStonesInMill(Stone.BLACK)).isFalse();
        }

        @Test
        @DisplayName("모든 돌이 밀에 속하면 true")
        void 모두_밀이면_true() {
            // given
            Board board = Board.parse(
                    """
                    B  B  B
                     . . .
                      ...
                    ... ...
                      ...
                     . . .
                    .  .  .
                    """);

            // then
            assertThat(board.allStonesInMill(Stone.BLACK)).isTrue();
        }
    }

    @Nested
    class 이동_가능_판정 {

        @Test
        @DisplayName("인접한 빈 지점이 있으면 움직일 수 있다")
        void 인접한_빈칸이_있으면_이동_가능() {
            // given
            Board board = new Board();
            board.place(0, Stone.BLACK);

            // then
            assertThat(board.hasLegalMove(Stone.BLACK, false)).isTrue();
        }

        @Test
        @DisplayName("이웃이 전부 막히면 움직일 수 없다")
        void 이웃이_막히면_이동_불가() {
            // given — 지점 0 의 이웃은 1 과 9 뿐이다
            Board board = new Board();
            board.place(0, Stone.BLACK);
            board.place(1, Stone.WHITE);
            board.place(9, Stone.WHITE);

            // then
            assertThat(board.hasLegalMove(Stone.BLACK, false)).isFalse();
        }

        @Test
        @DisplayName("flying 이면 인접이 막혀도 빈 지점이 있으면 움직일 수 있다")
        void flying_이면_인접이_막혀도_이동_가능() {
            // given
            Board board = new Board();
            board.place(0, Stone.BLACK);
            board.place(1, Stone.WHITE);
            board.place(9, Stone.WHITE);

            // then
            assertThat(board.hasLegalMove(Stone.BLACK, true)).isTrue();
        }

        @Test
        @DisplayName("돌이 없으면 움직일 수 없다")
        void 돌이_없으면_이동_불가() {
            // given
            Board board = new Board();

            // then
            assertThat(board.hasLegalMove(Stone.BLACK, false)).isFalse();
            assertThat(board.hasLegalMove(Stone.BLACK, true)).isFalse();
        }
    }

    @Test
    @DisplayName("범위를 벗어난 지점은 거부한다")
    void 범위를_벗어나면_거부한다() {
        // given
        Board board = new Board();

        // then
        assertThatThrownBy(() -> board.stoneAt(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> board.stoneAt(24)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> board.place(99, Stone.BLACK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("복사본은 원본과 분리된다")
    void 복사본은_원본과_분리된다() {
        // given
        Board original = new Board();
        original.place(0, Stone.BLACK);

        // when
        Board copy = original.copy();
        copy.place(1, Stone.WHITE);

        // then
        assertThat(original.isEmpty(1)).isTrue();
        assertThat(copy.stoneAt(0)).isEqualTo(Stone.BLACK);
    }
}
