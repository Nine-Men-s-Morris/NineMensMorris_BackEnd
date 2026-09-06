package com.ninemensmorris.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninemensmorris.core.board.Board;
import com.ninemensmorris.core.board.Stone;
import com.ninemensmorris.core.move.Move;
import com.ninemensmorris.core.move.MoveResult;
import com.ninemensmorris.core.move.RejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MorrisGameTest {

    private static final int NO_STONES_IN_HAND = 0;

    // 판을 그림으로 주고 배치 단계를 건너뛴 게임을 만든다
    private static MorrisGame midGame(String art, Stone turn) {
        return new MorrisGame(Board.parse(art), turn, NO_STONES_IN_HAND, NO_STONES_IN_HAND);
    }

    private static RejectReason reasonOf(MoveResult result) {
        return ((MoveResult.Rejected) result).reason();
    }

    @Nested
    class 배치_단계 {

        @Test
        @DisplayName("상대 차례에 두면 거절한다")
        void 남의_차례에_둘_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Place(0));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.NOT_YOUR_TURN);
            assertThat(game.board().isEmpty(0)).isTrue();
        }

        @Test
        @DisplayName("돌이 있는 지점에는 놓을 수 없다")
        void 찬_지점에는_놓을_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.Place(0));

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Place(0));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.OCCUPIED);
            assertThat(game.board().stoneAt(0)).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("판 밖의 좌표는 거절한다")
        void 범위를_벗어난_좌표는_거절한다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);

            // then
            assertThat(reasonOf(game.apply(Stone.BLACK, new Move.Place(-1)))).isEqualTo(RejectReason.OUT_OF_BOARD);
            assertThat(reasonOf(game.apply(Stone.BLACK, new Move.Place(24)))).isEqualTo(RejectReason.OUT_OF_BOARD);
            assertThat(reasonOf(game.apply(Stone.BLACK, new Move.Place(99)))).isEqualTo(RejectReason.OUT_OF_BOARD);
        }

        @Test
        @DisplayName("배치 단계에서는 판 위의 돌을 옮길 수 없다")
        void 배치_단계에는_이동할_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.Place(0));
            game.apply(Stone.WHITE, new Move.Place(9));

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(0, 1));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.WRONG_PHASE);
        }

        @Test
        @DisplayName("놓을 때마다 손에 든 돌이 줄고 턴이 넘어간다")
        void 손에_든_돌이_줄어든다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);

            // when
            game.apply(Stone.BLACK, new Move.Place(0));

            // then
            assertThat(game.inHand(Stone.BLACK)).isEqualTo(MorrisGame.STONES_PER_PLAYER - 1);
            assertThat(game.currentTurn()).isEqualTo(Stone.WHITE);
            assertThat(game.phaseOf(Stone.BLACK)).isEqualTo(Phase.PLACING);
        }
    }

    @Nested
    class 밀과_제거 {

        // 흑이 0,1 을 잡고 있고 2 에 놓으면 밀이 완성되는 국면
        private MorrisGame 밀_직전() {
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.Place(0));
            game.apply(Stone.WHITE, new Move.Place(9));
            game.apply(Stone.BLACK, new Move.Place(1));
            game.apply(Stone.WHITE, new Move.Place(10));
            return game;
        }

        @Test
        @DisplayName("밀을 만들면 턴이 넘어가지 않고 제거할 차례가 된다")
        void 밀을_만들면_제거_차례가_된다() {
            // given
            MorrisGame game = 밀_직전();

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Place(2));

            // then
            assertThat(result).isInstanceOf(MoveResult.MillFormed.class);
            assertThat(game.awaitingRemoval()).isTrue();
            assertThat(game.currentTurn()).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("제거할 차례에 다른 수를 두면 거절한다")
        void 제거를_미루고_다른_수를_둘_수_없다() {
            // given
            MorrisGame game = 밀_직전();
            game.apply(Stone.BLACK, new Move.Place(2));

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Place(3));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.REMOVAL_PENDING);
        }

        @Test
        @DisplayName("밀을 만들지 않았는데 제거하려 하면 거절한다")
        void 제거_권한_없이는_제거할_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.Place(0));

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Remove(0));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.REMOVAL_NOT_PENDING);
            assertThat(game.board().stoneAt(0)).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("자기 돌은 제거할 수 없다")
        void 자기_돌은_제거할_수_없다() {
            // given
            MorrisGame game = 밀_직전();
            game.apply(Stone.BLACK, new Move.Place(2));

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Remove(0));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.NOT_OPPONENT_STONE);
            assertThat(game.board().stoneAt(0)).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("빈 지점은 제거할 수 없다")
        void 빈_지점은_제거할_수_없다() {
            // given
            MorrisGame game = 밀_직전();
            game.apply(Stone.BLACK, new Move.Place(2));

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Remove(20));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.NOT_OPPONENT_STONE);
        }

        @Test
        @DisplayName("밀에 속한 상대 돌은 제거할 수 없다")
        void 밀에_속한_돌은_보호된다() {
            // given — 백은 21,22,23 밀과 밀 밖의 돌 9 를 갖고 있다
            MorrisGame game = midGame(
                    """
                    B  B  .
                     . . .
                      ...
                    W.. ..B
                      ...
                     . . .
                    W  W  W
                    """,
                    Stone.BLACK);
            // 흑이 14 -> 2 로 옮겨 0,1,2 밀을 만든다
            assertThat(game.apply(Stone.BLACK, new Move.Slide(14, 2))).isInstanceOf(MoveResult.MillFormed.class);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Remove(21));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.PROTECTED_BY_MILL);
            assertThat(game.board().stoneAt(21)).isEqualTo(Stone.WHITE);
        }

        @Test
        @DisplayName("밀 밖의 돌은 제거할 수 있다")
        void 밀_밖의_돌은_제거된다() {
            // given
            MorrisGame game = midGame(
                    """
                    B  B  .
                     . . .
                      ...
                    W.. ..B
                      ...
                     . . .
                    W  W  W
                    """,
                    Stone.BLACK);
            // 흑이 14 -> 2 로 옮겨 0,1,2 밀을 만든다
            assertThat(game.apply(Stone.BLACK, new Move.Slide(14, 2))).isInstanceOf(MoveResult.MillFormed.class);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Remove(9));

            // then
            assertThat(result).isInstanceOf(MoveResult.Applied.class);
            assertThat(game.board().isEmpty(9)).isTrue();
            assertThat(game.currentTurn()).isEqualTo(Stone.WHITE);
        }

        @Test
        @DisplayName("상대 돌이 전부 밀에 속하면 밀 속 돌도 제거할 수 있다")
        void 전부_밀이면_밀_속_돌도_제거된다() {
            // given — 백의 6개가 21,22,23 과 15,16,17 두 밀에 전부 들어 있다
            MorrisGame game = midGame(
                    """
                    B  B  .
                     . . .
                      ...
                    ... ..B
                      WWW
                     . . .
                    W  W  W
                    """,
                    Stone.BLACK);
            assertThat(game.apply(Stone.BLACK, new Move.Slide(14, 2))).isInstanceOf(MoveResult.MillFormed.class);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Remove(21));

            // then
            assertThat(result).isInstanceOf(MoveResult.Applied.class);
            assertThat(game.board().isEmpty(21)).isTrue();
        }
    }

    @Nested
    class 이동_단계 {

        // 흑 0,3,6 / 백 15,18,21 — 어느 쪽도 밀이 아니고 양쪽 다 3개라 FLYING
        private static final String 세개씩 =
                """
                B  .  .
                 B . .
                  B..
                ... ...
                  W..
                 W . .
                W  .  .
                """;

        // 흑 4개라 MOVING
        private static final String 흑이_네개 =
                """
                B  .  .
                 B . .
                  B..
                B.. ...
                  W..
                 W . .
                W  .  .
                """;

        @Test
        @DisplayName("MOVING 에서는 인접하지 않은 곳으로 갈 수 없다")
        void 인접하지_않으면_이동할_수_없다() {
            // given
            MorrisGame game = midGame(흑이_네개, Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(0, 23));

            // then
            assertThat(game.phaseOf(Stone.BLACK)).isEqualTo(Phase.MOVING);
            assertThat(reasonOf(result)).isEqualTo(RejectReason.NOT_ADJACENT);
            assertThat(game.board().stoneAt(0)).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("인접한 빈 지점으로는 이동할 수 있다")
        void 인접하면_이동한다() {
            // given
            MorrisGame game = midGame(흑이_네개, Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(0, 1));

            // then
            assertThat(result).isInstanceOf(MoveResult.Applied.class);
            assertThat(game.board().stoneAt(1)).isEqualTo(Stone.BLACK);
            assertThat(game.board().isEmpty(0)).isTrue();
        }

        @Test
        @DisplayName("상대 돌은 옮길 수 없다")
        void 상대_돌은_옮길_수_없다() {
            // given
            MorrisGame game = midGame(흑이_네개, Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(21, 22));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.NOT_YOUR_STONE);
            assertThat(game.board().stoneAt(21)).isEqualTo(Stone.WHITE);
        }

        @Test
        @DisplayName("빈 지점에서 출발할 수 없다")
        void 빈_지점에서_출발할_수_없다() {
            // given
            MorrisGame game = midGame(흑이_네개, Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(23, 22));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.EMPTY_SOURCE);
            assertThat(game.board().isEmpty(22)).isTrue();
        }

        @Test
        @DisplayName("돌이 있는 지점으로는 이동할 수 없다")
        void 찬_지점으로_이동할_수_없다() {
            // given
            MorrisGame game = midGame(흑이_네개, Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(0, 9));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.OCCUPIED);
        }

        @Test
        @DisplayName("돌이 3개면 FLYING 이라 인접하지 않은 곳으로도 갈 수 있다")
        void 세개면_어디로든_이동한다() {
            // given
            MorrisGame game = midGame(세개씩, Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Slide(0, 23));

            // then
            assertThat(game.phaseOf(Stone.BLACK)).isEqualTo(Phase.FLYING);
            assertThat(result).isInstanceOf(MoveResult.Applied.class);
            assertThat(game.board().stoneAt(23)).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("돌이 4개면 아직 FLYING 이 아니다")
        void 네개면_아직_FLYING_이_아니다() {
            // given
            MorrisGame game = midGame(흑이_네개, Stone.BLACK);

            // then
            assertThat(game.phaseOf(Stone.BLACK)).isEqualTo(Phase.MOVING);
            assertThat(game.phaseOf(Stone.WHITE)).isEqualTo(Phase.FLYING);
        }
    }

    @Nested
    class 승패 {

        @Test
        @DisplayName("돌이 2개가 되면 패배한다")
        void 두개가_되면_패배한다() {
            // given — 흑 3개(0,3,6) / 백 3개(14,21,22). 백이 14->23 으로 밀을 만든다
            MorrisGame game = midGame(
                    """
                    B  .  .
                     B . .
                      B..
                    ... ..W
                      ...
                     . . .
                    W  W  .
                    """,
                    Stone.WHITE);
            MoveResult mill = game.apply(Stone.WHITE, new Move.Slide(14, 23));
            assertThat(mill).isInstanceOf(MoveResult.MillFormed.class);

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Remove(0));

            // then
            assertThat(result).isInstanceOf(MoveResult.Finished.class);
            assertThat(game.outcome().winner()).isEqualTo(Stone.WHITE);
            assertThat(game.outcome().reason()).isEqualTo(EndReason.STONES_EXHAUSTED);
            assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
        }

        @Test
        @DisplayName("둘 수가 없으면 패배한다")
        void 이동할_수_없으면_패배한다() {
            // given — 흑 0,2,14,23 이 백 1,9,13,22 에 완전히 막혀 있다. 백은 3 을 움직일 수 있다
            MorrisGame game = midGame(
                    """
                    B  W  B
                     W . .
                      ...
                    W.. .WB
                      ...
                     . . .
                    .  W  B
                    """,
                    Stone.WHITE);

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Slide(3, 4));

            // then
            assertThat(result).isInstanceOf(MoveResult.Finished.class);
            assertThat(game.outcome().winner()).isEqualTo(Stone.WHITE);
            assertThat(game.outcome().reason()).isEqualTo(EndReason.NO_LEGAL_MOVE);
        }

        @Test
        @DisplayName("배치 단계에서는 판 위에서 못 움직여도 패배가 아니다")
        void 배치_단계에는_이동_불가로_패배하지_않는다() {
            // given — 흑 돌 0 이 백 1,9 에 막혀 있지만 흑은 손에 돌이 남아 있다
            MorrisGame game = new MorrisGame(
                    Board.parse(
                            """
                            B  W  .
                             . . .
                              ...
                            W.. ...
                              ...
                             . . .
                            .  .  .
                            """),
                    Stone.WHITE,
                    5,
                    5);

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Place(23));

            // then
            assertThat(result).isInstanceOf(MoveResult.Applied.class);
            assertThat(game.status()).isEqualTo(GameStatus.PLAYING);
            assertThat(game.currentTurn()).isEqualTo(Stone.BLACK);
        }

        @Test
        @DisplayName("기권하면 상대가 이긴다")
        void 기권하면_상대가_이긴다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.Resign());

            // then
            assertThat(result).isInstanceOf(MoveResult.Finished.class);
            assertThat(game.outcome().winner()).isEqualTo(Stone.WHITE);
            assertThat(game.outcome().reason()).isEqualTo(EndReason.RESIGN);
        }

        @Test
        @DisplayName("끝난 게임에는 더 둘 수 없다")
        void 끝난_게임에는_둘_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.Resign());

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.Place(0));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.GAME_NOT_IN_PROGRESS);
        }
    }

    @Nested
    class 무승부 {

        @Test
        @DisplayName("제안하고 상대가 수락하면 무승부다")
        void 합의하면_무승부다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.OfferDraw());

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.RespondDraw(true));

            // then
            assertThat(result).isInstanceOf(MoveResult.Finished.class);
            assertThat(game.outcome().isDraw()).isTrue();
            assertThat(game.outcome().reason()).isEqualTo(EndReason.DRAW_AGREED);
        }

        @Test
        @DisplayName("제안이 없으면 수락할 수 없다")
        void 제안_없이_수락할_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);

            // when
            MoveResult result = game.apply(Stone.WHITE, new Move.RespondDraw(true));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.NO_DRAW_OFFER);
            assertThat(game.status()).isEqualTo(GameStatus.PLAYING);
        }

        @Test
        @DisplayName("자기가 한 제안을 자기가 수락할 수 없다")
        void 자기_제안을_자기가_수락할_수_없다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.OfferDraw());

            // when
            MoveResult result = game.apply(Stone.BLACK, new Move.RespondDraw(true));

            // then
            assertThat(reasonOf(result)).isEqualTo(RejectReason.CANNOT_ACCEPT_OWN_OFFER);
            assertThat(game.status()).isEqualTo(GameStatus.PLAYING);
        }

        @Test
        @DisplayName("거절하면 제안이 사라진다")
        void 거절하면_제안이_사라진다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.OfferDraw());

            // when
            game.apply(Stone.WHITE, new Move.RespondDraw(false));

            // then
            assertThat(game.drawOfferedBy()).isNull();
            assertThat(reasonOf(game.apply(Stone.WHITE, new Move.RespondDraw(true))))
                    .isEqualTo(RejectReason.NO_DRAW_OFFER);
        }

        @Test
        @DisplayName("상대가 수를 두면 제안이 소멸한다")
        void 수를_두면_제안이_소멸한다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.OfferDraw());

            // when
            game.apply(Stone.BLACK, new Move.Place(0));

            // then
            assertThat(game.drawOfferedBy()).isNull();
        }

        @Test
        @DisplayName("같은 국면이 3번 반복되면 무승부다")
        void 삼회_반복이면_무승부다() {
            // given — 양쪽 3개씩이라 FLYING. 서로 왕복만 한다
            MorrisGame game = midGame(
                    """
                    B  .  .
                     B . .
                      B..
                    ... ...
                      W..
                     W . .
                    W  .  .
                    """,
                    Stone.BLACK);

            // when — 한 번 왕복하면 최초 국면으로 돌아온다
            MoveResult result = null;
            for (int cycle = 0; cycle < 2; cycle++) {
                game.apply(Stone.BLACK, new Move.Slide(0, 1));
                game.apply(Stone.WHITE, new Move.Slide(21, 22));
                game.apply(Stone.BLACK, new Move.Slide(1, 0));
                result = game.apply(Stone.WHITE, new Move.Slide(22, 21));
            }

            // then
            assertThat(result).isInstanceOf(MoveResult.Finished.class);
            assertThat(game.outcome().isDraw()).isTrue();
            assertThat(game.outcome().reason()).isEqualTo(EndReason.THREEFOLD_REPETITION);
        }
    }

    @Nested
    class 제거없는_수_카운터 {

        @Test
        @DisplayName("수를 둘 때마다 늘어난다")
        void 수마다_늘어난다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);

            // when
            game.apply(Stone.BLACK, new Move.Place(0));
            game.apply(Stone.WHITE, new Move.Place(9));

            // then
            assertThat(game.movesSinceRemoval()).isEqualTo(2);
        }

        @Test
        @DisplayName("제거가 일어나면 0으로 돌아간다")
        void 제거하면_초기화된다() {
            // given
            MorrisGame game = new MorrisGame(Stone.BLACK);
            game.apply(Stone.BLACK, new Move.Place(0));
            game.apply(Stone.WHITE, new Move.Place(9));
            game.apply(Stone.BLACK, new Move.Place(1));
            game.apply(Stone.WHITE, new Move.Place(10));
            game.apply(Stone.BLACK, new Move.Place(2));
            assertThat(game.movesSinceRemoval()).isEqualTo(4);

            // when
            game.apply(Stone.BLACK, new Move.Remove(9));

            // then
            assertThat(game.movesSinceRemoval()).isZero();
        }
    }
}
