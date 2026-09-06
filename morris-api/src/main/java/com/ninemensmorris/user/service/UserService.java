package com.ninemensmorris.user.service;

import com.ninemensmorris.common.exception.CustomException;
import com.ninemensmorris.common.response.ErrorCode;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.dto.response.MyProfileResponse;
import com.ninemensmorris.user.dto.response.NicknameResponse;
import com.ninemensmorris.user.dto.response.RankingResponse;
import com.ninemensmorris.user.repository.UserRepository;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    // 요청자는 파라미터로 받는다. SecurityContextHolder 를 직접 읽으면
    // STOMP 스레드에서는 비어 있고 비인증이면 "anonymousUser" 가 나와 500 이 됐다
    public MyProfileResponse findMe(long userId) {
        User user = find(userId);
        return MyProfileResponse.of(user, userRepository.findRankByMmr(user.getMmr()));
    }

    public NicknameResponse findNickname(long userId) {
        return new NicknameResponse(find(userId).getNickname());
    }

    public List<RankingResponse> findRankings(int limit) {
        List<User> top = userRepository.findAllByOrderByMmrDesc(PageRequest.of(0, limit));
        return IntStream.range(0, top.size())
                .mapToObj(index -> RankingResponse.of(index + 1, top.get(index)))
                .toList();
    }

    private User find(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_USER));
    }
}
