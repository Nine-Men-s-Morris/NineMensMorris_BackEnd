
<img src="https://hackmd.io/_uploads/HyAvl0yWR.png" width="128px" />

## Nine men's Morris | 나인멘스모리스
> ### 배포 링크 - ~~https://ninemensmorris.site~~
> [나인멘스모리스 작업 노션](https://www.notion.so/ldhbenecia/Side-Project-f19f6b2d80074a8da597b0f0d8a7f07e?pvs=4)

<br />

## Playing Images

![](https://github.com/Nine-Men-s-Morris/.github/blob/main/images/0.png?raw=true)

![](https://github.com/Nine-Men-s-Morris/.github/blob/main/images/1.png?raw=true)

![](https://github.com/Nine-Men-s-Morris/.github/blob/main/images/2.png?raw=true)

![](https://github.com/Nine-Men-s-Morris/.github/blob/main/images/3.png?raw=true)

<br />

## 기술 스택
| 분류 | 스택 |
| --- | --- |
|    FrontEnd   | <img src="https://img.shields.io/badge/React-008dde?logo=react&logoColor=white"/>  <img src="https://img.shields.io/badge/React_Query-FF4154?logo=reactquery&logoColor=white"/> <img src="https://img.shields.io/badge/React_Router-7F1F21?logo=reactrouter&logoColor=white"/> <img src="https://img.shields.io/badge/Tailwind CSS-06B6D4?logo=tailwindcss&logoColor=white"/> <img src="https://img.shields.io/badge/STOMP.js-010101?logo=socket.io&logoColor=white"/> |
|    BackEnd    | <img src="https://img.shields.io/badge/Java-007396?logo=openjdk&logoColor=white"/>  <img src="https://img.shields.io/badge/SpringBoot-6DB33F?logo=springboot&logoColor=white"/> <img src="https://img.shields.io/badge/Spring Security-6DB33F?logo=springsecurity&logoColor=white"/>  <img src="https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white"/> <img src="https://img.shields.io/badge/STOMP-010101?logo=socket.io&logoColor=white"/> |
|     Infra     | <img src="https://img.shields.io/badge/Amazon EC2-FF9900?logo=amazonec2&logoColor=white"/> <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white"/> <img src="https://img.shields.io/badge/Docker_Compose-2496ED?logo=docker&logoColor=white"/> <img src="https://img.shields.io/badge/Nginx-009639?logo=nginx&&labelColor=009639"/>  <img src="https://img.shields.io/badge/Certbot-E20722?logo=robotframework"/>   |
| Collaboration |  <img src="https://img.shields.io/badge/Notion-000000?logo=Notion"> <img src="https://img.shields.io/badge/Figma-F24E1E?logo=Figma&logoColor=ffffff"> <img src="https://img.shields.io/badge/Discord-5865F2?logo=Discord&logoColor=ffffff">  |

<br />

## 아키텍처
![image](https://hackmd.io/_uploads/Hy3KFaJW0.png)

<br />

## 로컬 실행

### 준비물

- JDK 21 (Gradle toolchain 이 자동으로 받지만 로컬에 있으면 빠름)
- Docker (MySQL 및 테스트용 Testcontainers 에 필요)

### 1. 환경변수

```bash
cp .env.example .env
```

| 키 | 설명 |
| --- | --- |
| `MYSQL_*` | docker-compose 가 띄우는 MySQL 계정·DB명·포트 |
| `SPRING_DATASOURCE_*` | 앱을 **호스트에서 직접** 실행할 때 쓰는 접속 정보 (compose 로 띄우면 자동 주입) |
| `JPA_DDL_AUTO` | 로컬은 `update`, 운영은 `validate` |
| `JWT_SECRET_KEY` | HS256 서명 키. Base64 32바이트 이상 — `openssl rand -base64 32` |
| `ACCESS_TOKEN_EXPIRATION` | 밀리초 단위 (1시간 = `3600000`) |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | [카카오 개발자센터](https://developers.kakao.com) 앱 키. Redirect URI 에 `{서버주소}/api/oauth2/kakao` 등록 필요 |
| `DOMAIN` | 로그인 성공 후 리다이렉트할 프론트엔드 주소 |
| `SERVER_PORT` | 기본 `8080` |

### 2. 실행

```bash
docker compose up --build        # 앱 + MySQL
docker compose up -d database    # DB 만 띄우고 앱은 IDE 에서 실행할 때
```

### 3. 확인

```bash
curl http://localhost:8080/actuator/health
```

<br />

## 개발

```bash
./gradlew build            # 컴파일 + 테스트
./gradlew test             # 테스트만 (Docker 필요 — Testcontainers 로 MySQL 을 띄움)
./gradlew spotlessApply    # 포맷 적용 — 커밋 전 필수
./gradlew spotlessCheck    # 포맷 검사 (CI 에서 수행)
```

<br />

## 문서

`docs/plans/` 에 2026년 기준 전수조사 결과와 개선 계획이 있다.
작업 전에 [docs/plans/README.md](docs/plans/README.md) 부터 읽는 것을 권한다.

| 문서 | 내용 |
| --- | --- |
| [13. 실행 로드맵](docs/plans/13-roadmap.md) | 작업 시작 지점. Phase 0~9 |
| [01. 코드 전수조사](docs/plans/01-code-audit.md) | 알려진 결함 목록 |
| [02. 게임 규칙 대조표](docs/plans/02-game-rules-audit.md) | 공식 규칙 22개 대조 |
| [07. 아키텍처 결정](docs/plans/07-architecture-decision.md) | ADR 7건 |
