package com.ninemensmorris.user.dto.response;

import com.ninemensmorris.core.rating.Tier;
import com.ninemensmorris.user.domain.User;

public record RankingResponse(
        int rank, long userId, String nickname, String imageUrl, int mmr, Tier tier, int wins, int losses, int draws) {

    public static RankingResponse of(int rank, User user) {
        return new RankingResponse(
                rank,
                user.getUserId(),
                user.getNickname(),
                user.getImageUrl(),
                user.getMmr(),
                Tier.of(user.getMmr(), user.gamesPlayed()),
                user.getWins(),
                user.getLosses(),
                user.getDraws());
    }
}
