package com.ninemensmorris.game.dto.request;

// 1단계 배치. gameId 도 userId 도 없다 — roomId 는 목적지 경로에서, 행위자는 Principal 에서 온다
public record PlaceRequest(int to) {}
