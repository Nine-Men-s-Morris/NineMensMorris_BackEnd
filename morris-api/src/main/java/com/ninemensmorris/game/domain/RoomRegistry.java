package com.ninemensmorris.game.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 방 저장소
//
// 기존에는 gameId 하나의 상태가 동기화 없는 HashMap 11개에 흩어져 있었다
// STOMP 메시지는 스레드 풀에서 병렬 처리되므로 락 없이 접근하면 값이 유실된다
// 방 하나당 락을 걸어 직렬화하고, 방끼리는 그대로 병렬로 처리한다
@Component
@Slf4j
public class RoomRegistry {

    // 이 시간 동안 아무 일도 없으면 정리 대상
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final ConcurrentMap<Long, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Room create(String title, long hostId) {
        long roomId = sequence.incrementAndGet();
        Room room = new Room(roomId, title, hostId);
        rooms.put(roomId, room);
        return room;
    }

    public Optional<Room> find(long roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public List<Room> findAll() {
        return rooms.values().stream()
                .sorted(Comparator.comparing(Room::createdAt).reversed())
                .toList();
    }

    // 사용자가 참여 중인 방. 소켓이 끊겼을 때 찾는 용도
    public Optional<Room> findByPlayer(long userId) {
        return rooms.values().stream().filter(room -> room.contains(userId)).findFirst();
    }

    public void remove(long roomId) {
        rooms.remove(roomId);
    }

    public int size() {
        return rooms.size();
    }

    // 방 하나에 대한 모든 변경은 이 메서드를 거친다
    public <R> Optional<R> mutate(long roomId, Function<Room, R> action) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return Optional.empty();
        }
        synchronized (room) {
            R result = action.apply(room);
            room.touch();
            return Optional.ofNullable(result);
        }
    }

    // 방치된 방 정리. 기존에는 정리 대상을 판별할 시각 정보조차 없어 고아 방이 쌓였다
    @Scheduled(fixedDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void purgeIdleRooms() {
        Instant threshold = Instant.now().minus(IDLE_TIMEOUT);
        List<Long> stale = rooms.values().stream()
                .filter(room -> room.lastActivityAt().isBefore(threshold))
                .map(Room::roomId)
                .toList();

        stale.forEach(rooms::remove);
        if (!stale.isEmpty()) {
            log.info("유휴 방 정리 완료. 삭제 {}건, 남은 방 {}개", stale.size(), rooms.size());
        }
    }
}
