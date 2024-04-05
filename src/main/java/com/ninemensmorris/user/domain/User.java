package com.ninemensmorris.user.domain;

import com.ninemensmorris.game.domain.GameRoom;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String email;
    private String nickname;
    private String imageUrl;
    private String role;
    private int score;
}