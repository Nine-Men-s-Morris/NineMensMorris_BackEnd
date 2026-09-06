package com.ninemensmorris.game.service;

import com.ninemensmorris.game.dto.response.MoveRejectedResponse;
import com.ninemensmorris.game.dto.response.RoomEvent;

// 착수 처리 결과
// 거절은 요청자에게만 보내고 방 전체에 뿌리지 않는다
public sealed interface PlayOutcome {

    record Broadcast(RoomEvent event) implements PlayOutcome {}

    record Reject(MoveRejectedResponse response) implements PlayOutcome {}
}
