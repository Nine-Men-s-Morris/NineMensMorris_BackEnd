package com.ninemensmorris.game.domain;

import com.ninemensmorris.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class GameRoom {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_room_id")
    private Long id;

    private Long roomId;

    @OneToMany(mappedBy = "gameRoom", cascade = CascadeType.ALL)
    private Set<User> users = new HashSet<>();

    public void addUser(User user) {
        users.add(user);
        user.setGameRoom(this);
    }

    public void removeUser(User user) {
        users.remove(user);
        user.setGameRoom(null);
    }
}
