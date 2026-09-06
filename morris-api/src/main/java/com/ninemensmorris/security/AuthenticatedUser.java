package com.ninemensmorris.security;

import java.security.Principal;

// 서버가 확정한 요청자
//
// 컨트롤러가 Principal 에서 꺼내 Command 로 넘긴다
// 서비스가 SecurityContextHolder 를 직접 읽으면 STOMP 스레드에서는 비어 있고,
// 클라이언트 페이로드의 userId 를 믿으면 남의 계정으로 조작할 수 있다
public record AuthenticatedUser(long id) {

    // 인증되지 않았으면 null. 호출부가 401 로 처리한다
    public static AuthenticatedUser from(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return new AuthenticatedUser(Long.parseLong(principal.getName()));
        } catch (NumberFormatException notAuthenticated) {
            // 비인증이면 이름이 "anonymousUser" 다
            return null;
        }
    }
}
