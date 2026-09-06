package com.ninemensmorris.core.move;

// 플레이어가 두려는 수
// 좌표만 담고 "누가" 는 담지 않음. 행위자는 apply 의 인자로 서버가 확정해서 넘김
public sealed interface Move {

    // 1단계. 손에 든 돌을 빈 지점에 놓음
    record Place(int to) implements Move {}

    // 2·3단계. 판 위의 자기 돌을 빈 지점으로 옮김
    record Slide(int from, int to) implements Move {}

    // 밀을 만든 직후 상대 돌 하나를 제거
    record Remove(int at) implements Move {}

    // 기권. 상대가 이김
    record Resign() implements Move {}

    // 무승부 제안. 상대가 수를 두면 소멸함
    record OfferDraw() implements Move {}

    // 무승부 제안에 대한 응답
    record RespondDraw(boolean accept) implements Move {}
}
