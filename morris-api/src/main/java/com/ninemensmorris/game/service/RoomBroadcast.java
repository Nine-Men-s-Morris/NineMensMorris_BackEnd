package com.ninemensmorris.game.service;

import com.ninemensmorris.game.dto.response.RoomEvent;

// 어느 방으로 보낼 이벤트인지. 처리 후 방이 사라질 수 있어 roomId 를 함께 돌려준다
public record RoomBroadcast(long roomId, RoomEvent event) {}
