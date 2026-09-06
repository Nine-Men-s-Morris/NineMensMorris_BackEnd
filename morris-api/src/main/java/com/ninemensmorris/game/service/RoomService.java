package com.ninemensmorris.game.service;

import com.ninemensmorris.common.exception.CustomException;
import com.ninemensmorris.common.response.ErrorCode;
import com.ninemensmorris.game.command.RoomCommand;
import com.ninemensmorris.game.domain.Room;
import com.ninemensmorris.game.domain.RoomRegistry;
import com.ninemensmorris.game.dto.response.CreateRoomResponse;
import com.ninemensmorris.game.dto.response.RoomSummaryResponse;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 로비. 방 생성 / 입장 / 퇴장 / 목록
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final RoomRegistry rooms;
    private final UserRepository userRepository;

    public List<RoomSummaryResponse> findAll() {
        List<Room> found = rooms.findAll();
        if (found.isEmpty()) {
            return List.of();
        }

        // 방마다 방장을 조회하면 N+1 이 된다. 한 번에 가져와 맞춘다
        Map<Long, User> hosts =
                userRepository
                        .findAllById(found.stream().map(Room::hostId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity()));

        return found.stream()
                .map(room -> {
                    User host = hosts.get(room.hostId());
                    return RoomSummaryResponse.of(
                            room,
                            host == null ? "알 수 없음" : host.getNickname(),
                            host == null ? null : host.getImageUrl(),
                            host == null ? 0 : host.getMmr());
                })
                .toList();
    }

    public CreateRoomResponse create(RoomCommand.CreateRoom command) {
        Room room = rooms.create(command.title(), command.actorId());
        log.info("방 생성 roomId={} hostId={}", room.roomId(), command.actorId());
        return new CreateRoomResponse(room.roomId(), room.title());
    }

    // 입장은 방 단위 락 안에서 처리한다
    // 기존에는 playerTwoId == null 을 확인하고 저장하는 사이에 다른 요청이 끼어들 수 있었다
    public void join(RoomCommand.JoinRoom command) {
        RejectableResult result = rooms.mutate(command.roomId(), room -> {
                    if (room.contains(command.actorId())) {
                        return RejectableResult.ALREADY_JOINED;
                    }
                    if (room.isFull()) {
                        return RejectableResult.FULL;
                    }
                    room.join(command.actorId());
                    return RejectableResult.OK;
                })
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        switch (result) {
            case ALREADY_JOINED -> throw new CustomException(ErrorCode.ALREADY_JOINED);
            case FULL -> throw new CustomException(ErrorCode.ROOM_FULL);
            case OK -> log.info("방 입장 roomId={} userId={}", command.roomId(), command.actorId());
        }
    }

    // 방장이 나가면 방이 사라지고, 참가자가 나가면 방은 대기 상태로 돌아간다
    public void leave(RoomCommand.LeaveRoom command) {
        Room room = rooms.find(command.roomId()).orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        if (room.hostId() == command.actorId()) {
            rooms.remove(command.roomId());
            log.info("방 삭제 roomId={} 방장 퇴장", command.roomId());
            return;
        }

        rooms.mutate(command.roomId(), r -> {
            if (r.contains(command.actorId())) {
                r.leaveGuest();
            }
            return true;
        });
        log.info("방 퇴장 roomId={} userId={}", command.roomId(), command.actorId());
    }

    public Optional<Room> find(long roomId) {
        return rooms.find(roomId);
    }

    private enum RejectableResult {
        OK,
        FULL,
        ALREADY_JOINED
    }
}
