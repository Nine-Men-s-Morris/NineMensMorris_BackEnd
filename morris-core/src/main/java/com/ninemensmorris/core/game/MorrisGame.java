package com.ninemensmorris.core.game;

import com.ninemensmorris.core.board.Adjacency;
import com.ninemensmorris.core.board.Board;
import com.ninemensmorris.core.board.Stone;
import com.ninemensmorris.core.move.Move;
import com.ninemensmorris.core.move.MoveResult;
import com.ninemensmorris.core.move.RejectReason;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

// 나인멘스모리스 규칙 엔진
//
// 모든 착수는 apply 를 거치며, 규칙에 어긋나면 Rejected 를 반환하고 판을 바꾸지 않음
// 기존 구현은 클라이언트가 보낸 좌표를 그대로 배열에 써서 규칙 강제가 프론트에만 있었음
public final class MorrisGame {

    public static final int STONES_PER_PLAYER = 9;

    // 돌이 3개면 인접 제약이 사라지고, 2개가 되면 패배
    private static final int FLYING_STONES = 3;

    // 양쪽 합쳐 이만큼 두는 동안 제거가 없으면 무승부
    private static final int NO_REMOVAL_DRAW_LIMIT = 50;

    private static final int REPETITION_DRAW_LIMIT = 3;

    private final Board board;
    private final Map<Stone, Integer> inHand = new EnumMap<>(Stone.class);
    private final Map<String, Integer> seenPositions = new HashMap<>();

    private Stone currentTurn;
    private GameStatus status = GameStatus.PLAYING;
    private Outcome outcome;

    // 밀을 만들어 제거할 권한이 있는 상태. 기존에는 응답에만 담기고 서버가 기억하지 않았음
    private boolean awaitingRemoval;

    private Stone drawOfferedBy;
    private int movesSinceRemoval;

    public MorrisGame(Stone firstTurn) {
        this(new Board(), firstTurn, STONES_PER_PLAYER, STONES_PER_PLAYER);
    }

    // 중간 국면에서 시작. 테스트와 재접속 복구에 사용
    public MorrisGame(Board board, Stone firstTurn, int blackInHand, int whiteInHand) {
        this.board = board;
        this.currentTurn = firstTurn;
        this.inHand.put(Stone.BLACK, blackInHand);
        this.inHand.put(Stone.WHITE, whiteInHand);
        recordPosition();
    }

    // 유일한 진입점. 여기를 거치지 않고 판이 바뀌는 경로는 없어야 함
    public MoveResult apply(Stone actor, Move move) {
        if (status != GameStatus.PLAYING) {
            return reject(RejectReason.GAME_NOT_IN_PROGRESS);
        }
        return switch (move) {
            case Move.Resign ignored -> finish(Outcome.win(actor.opponent(), EndReason.RESIGN));
            case Move.OfferDraw ignored -> offerDraw(actor);
            case Move.RespondDraw respond -> respondDraw(actor, respond.accept());
            case Move.Place place -> place(actor, place.to());
            case Move.Slide slide -> slide(actor, slide.from(), slide.to());
            case Move.Remove remove -> remove(actor, remove.at());
        };
    }

    // 1단계. 손에 든 돌을 빈 지점에 놓음
    // 이미 돌이 있는 자리에 덮어쓰는 것을 막는다. 기존에는 검사 없이 배열에 대입했음
    private MoveResult place(Stone actor, int to) {
        RejectReason turnProblem = checkTurn(actor);
        if (turnProblem != null) {
            return reject(turnProblem);
        }
        if (phaseOf(actor) != Phase.PLACING) {
            return reject(RejectReason.WRONG_PHASE);
        }
        if (isOutOfBoard(to)) {
            return reject(RejectReason.OUT_OF_BOARD);
        }
        if (!board.isEmpty(to)) {
            return reject(RejectReason.OCCUPIED);
        }

        board.place(to, actor);
        inHand.merge(actor, -1, Integer::sum);
        return afterStoneLanded(to);
    }

    // 2·3단계. 판 위의 자기 돌을 빈 지점으로 옮김
    // MOVING 은 인접한 곳으로만, FLYING(돌 3개)은 아무 빈 지점으로나 갈 수 있음
    // 기존에는 인접 검사도 소유권 검사도 없어 상대 돌을 반대편으로 순간이동시킬 수 있었음
    private MoveResult slide(Stone actor, int from, int to) {
        RejectReason turnProblem = checkTurn(actor);
        if (turnProblem != null) {
            return reject(turnProblem);
        }
        Phase phase = phaseOf(actor);
        if (phase == Phase.PLACING) {
            return reject(RejectReason.WRONG_PHASE);
        }
        if (isOutOfBoard(from) || isOutOfBoard(to)) {
            return reject(RejectReason.OUT_OF_BOARD);
        }
        if (board.isEmpty(from)) {
            return reject(RejectReason.EMPTY_SOURCE);
        }
        if (!board.isOccupiedBy(from, actor)) {
            return reject(RejectReason.NOT_YOUR_STONE);
        }
        if (!board.isEmpty(to)) {
            return reject(RejectReason.OCCUPIED);
        }
        if (phase == Phase.MOVING && !Adjacency.isAdjacent(from, to)) {
            return reject(RejectReason.NOT_ADJACENT);
        }

        board.move(from, to);
        return afterStoneLanded(to);
    }

    // 밀을 만든 직후 상대 돌 하나를 제거
    // 밀에 속한 상대 돌은 보호되지만, 상대 돌이 전부 밀에 속해 있으면 그마저 제거할 수 있음
    // 이 예외가 없으면 제거할 대상이 하나도 없어 게임이 멈춘다
    private MoveResult remove(Stone actor, int at) {
        if (actor != currentTurn) {
            return reject(RejectReason.NOT_YOUR_TURN);
        }
        if (!awaitingRemoval) {
            return reject(RejectReason.REMOVAL_NOT_PENDING);
        }
        if (isOutOfBoard(at)) {
            return reject(RejectReason.OUT_OF_BOARD);
        }

        Stone opponent = actor.opponent();
        if (!board.isOccupiedBy(at, opponent)) {
            return reject(RejectReason.NOT_OPPONENT_STONE);
        }
        if (board.isInMill(at) && !board.allStonesInMill(opponent)) {
            return reject(RejectReason.PROTECTED_BY_MILL);
        }

        board.clear(at);
        awaitingRemoval = false;
        return endTurn(true);
    }

    // 무승부 제안. 상대가 수를 두면 자동으로 소멸함
    private MoveResult offerDraw(Stone actor) {
        drawOfferedBy = actor;
        return new MoveResult.Applied();
    }

    // 무승부 응답. 제안이 없거나 자기 제안이면 거절한다
    // 기존에는 상태가 없어 제안한 적 없어도 수락으로 게임을 끝낼 수 있었음
    private MoveResult respondDraw(Stone actor, boolean accept) {
        if (drawOfferedBy == null) {
            return reject(RejectReason.NO_DRAW_OFFER);
        }
        if (drawOfferedBy == actor) {
            return reject(RejectReason.CANNOT_ACCEPT_OWN_OFFER);
        }
        if (!accept) {
            drawOfferedBy = null;
            return new MoveResult.Applied();
        }
        return finish(Outcome.draw(EndReason.DRAW_AGREED));
    }

    // 돌이 자리를 잡은 직후 공통 처리
    // 밀이 생겼으면 턴을 넘기지 않고 제거 권한만 준다
    private MoveResult afterStoneLanded(int to) {
        drawOfferedBy = null;
        if (board.isInMill(to)) {
            awaitingRemoval = true;
            return new MoveResult.MillFormed();
        }
        return endTurn(false);
    }

    // 턴을 넘기고 종료 조건을 확인
    // 판정 대상은 "다음에 둘 사람"이다. 둘 수가 없으면 그 사람이 진다
    private MoveResult endTurn(boolean removalHappened) {
        movesSinceRemoval = removalHappened ? 0 : movesSinceRemoval + 1;
        currentTurn = currentTurn.opponent();

        if (isDefeated(currentTurn)) {
            EndReason reason = hasTooFewStones(currentTurn) ? EndReason.STONES_EXHAUSTED : EndReason.NO_LEGAL_MOVE;
            return finish(Outcome.win(currentTurn.opponent(), reason));
        }
        if (recordPosition() >= REPETITION_DRAW_LIMIT) {
            return finish(Outcome.draw(EndReason.THREEFOLD_REPETITION));
        }
        if (movesSinceRemoval >= NO_REMOVAL_DRAW_LIMIT) {
            return finish(Outcome.draw(EndReason.FIFTY_MOVE_RULE));
        }
        return new MoveResult.Applied();
    }

    private MoveResult finish(Outcome result) {
        this.outcome = result;
        this.status = GameStatus.FINISHED;
        this.awaitingRemoval = false;
        this.drawOfferedBy = null;
        return new MoveResult.Finished(result);
    }

    // 패배 조건은 두 가지 — 돌이 2개 이하이거나 둘 수가 없거나
    // 손에 돌이 남아 있으면 놓으면 되므로 배치 단계에서는 패배하지 않는다
    private boolean isDefeated(Stone stone) {
        if (inHand.get(stone) > 0) {
            return false;
        }
        if (hasTooFewStones(stone)) {
            return true;
        }
        return !board.hasLegalMove(stone, phaseOf(stone) == Phase.FLYING);
    }

    private boolean hasTooFewStones(Stone stone) {
        return board.count(stone) < FLYING_STONES;
    }

    // 자기 차례가 아니거나, 제거할 차례인데 다른 수를 두려는 경우를 걸러냄
    private RejectReason checkTurn(Stone actor) {
        if (actor != currentTurn) {
            return RejectReason.NOT_YOUR_TURN;
        }
        if (awaitingRemoval) {
            return RejectReason.REMOVAL_PENDING;
        }
        return null;
    }

    private boolean isOutOfBoard(int point) {
        return point < 0 || point >= Board.POINTS;
    }

    private MoveResult reject(RejectReason reason) {
        return new MoveResult.Rejected(reason);
    }

    // 같은 국면이 몇 번째로 나타났는지 센다
    // 손에 든 돌 수까지 키에 넣어야 배치 단계와 이동 단계의 같은 배치를 구분한다
    private int recordPosition() {
        String key =
                "%s|%s|%d|%d".formatted(board.render(), currentTurn, inHand.get(Stone.BLACK), inHand.get(Stone.WHITE));
        return seenPositions.merge(key, 1, Integer::sum);
    }

    // 단계는 상태가 아니라 손에 든 돌과 판 위의 돌 수에서 계산된다
    public Phase phaseOf(Stone stone) {
        if (inHand.get(stone) > 0) {
            return Phase.PLACING;
        }
        return board.count(stone) == FLYING_STONES ? Phase.FLYING : Phase.MOVING;
    }

    // 밖에서 판을 고칠 수 없도록 복사본을 준다
    public Board board() {
        return board.copy();
    }

    public Stone currentTurn() {
        return currentTurn;
    }

    public GameStatus status() {
        return status;
    }

    public Outcome outcome() {
        return outcome;
    }

    public boolean awaitingRemoval() {
        return awaitingRemoval;
    }

    public Stone drawOfferedBy() {
        return drawOfferedBy;
    }

    public int inHand(Stone stone) {
        return inHand.get(stone);
    }

    public int onBoard(Stone stone) {
        return board.count(stone);
    }

    // 제거 없이 지나간 수. NO_REMOVAL_DRAW_LIMIT 에 닿으면 무승부
    public int movesSinceRemoval() {
        return movesSinceRemoval;
    }
}
