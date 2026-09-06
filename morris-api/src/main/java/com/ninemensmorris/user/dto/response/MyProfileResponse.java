package com.ninemensmorris.user.dto.response;

import com.ninemensmorris.core.rating.Tier;
import com.ninemensmorris.user.domain.User;

public record MyProfileResponse(
        long userId,
        String nickname,
        String imageUrl,
        int mmr,
        int peakMmr,
        Tier tier,
        int wins,
        int losses,
        int draws,
        Integer rank) {

    public static MyProfileResponse of(User user, Integer rank) {
        return new MyProfileResponse(
                user.getUserId(),
                user.getNickname(),
                user.getImageUrl(),
                user.getMmr(),
                user.getPeakMmr(),
                Tier.of(user.getMmr(), user.gamesPlayed()),
                user.getWins(),
                user.getLosses(),
                user.getDraws(),
                rank);
    }
}
