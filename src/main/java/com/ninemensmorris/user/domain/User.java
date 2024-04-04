package com.ninemensmorris.user.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    private Long providerId;

    private String email;
    private String nickname;
    private String imageUrl;
    private int score;
}