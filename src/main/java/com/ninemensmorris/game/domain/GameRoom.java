package com.ninemensmorris.game.domain;

import com.ninemensmorris.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class GameRoom {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_room_id")
    private Long id;

    private String roomTitle;
    private String host;
    private String playerOneNickname;
    private String playerTwoNickname;
}
