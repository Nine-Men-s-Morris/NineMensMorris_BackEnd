# Git / 커밋 컨벤션

## 브랜치

- `develop` 단일 트렁크 (origin/HEAD 가 develop)
- 작업 브랜치가 필요하면 prefix 풀네임: `feature/<slug>`, `fix/<slug>`, `refactor/<slug>`, `docs/<slug>`, `chore/<slug>`
- 소규모 정리·문서는 develop 직접 push 허용 (1인 프로젝트). 구조를 바꾸는 작업은 브랜치 + PR

## 커밋 (Conventional Commits)

- 형식: `type(scope): subject`
- type: `feat` `fix` `refactor` `style` `docs` `test` `build` `chore` `perf` `ci`
- scope 예: `game`, `auth`, `user`, `core`, `storage`, `support`, `api`, `config`, `deps`, `logging`
- **subject 톤**: 한국어 명사구 우선. 영어 명령형 동사(`add`, `update`)는 피함
  - 좋음: `feat(core): Board — 24지점 상태 + ASCII 표기법`
  - 피함: `feat(core): add board class`
- **body 톤**: bullet (`- `) 기본. 문단은 배경 설명이 꼭 필요할 때만
  - 한 줄에 한 변경/이유
- **WHY 를 적는다. WHAT 은 diff 가 말해준다**
- footer: `Refs:`, `Closes #N`, `BREAKING CHANGE:`

## 의미 단위 커밋

- 한 커밋 = 한 가지 변경 + **빌드/테스트가 깨지지 않는 상태**
- 삭제·포맷·리네임은 기능 변경과 섞지 않는다. 각각 단독 커밋
- 좋은 분할 예 (규칙 엔진 도입):
  1. `build: 4모듈 구조로 전환`
  2. `feat(core): Board / Mills / Adjacency + 테스트`
  3. `feat(core): MorrisGame — 착수 검증`
  4. `refactor(game): MorrisService 를 morris-core 로 교체`

## 커밋 전 확인

```bash
./gradlew spotlessApply    # 포맷 (필수)
./gradlew build            # 컴파일 + 테스트
```

**빌드를 돌리지 않고 커밋하지 않는다.** "될 것 같다"는 근거가 아니다.
