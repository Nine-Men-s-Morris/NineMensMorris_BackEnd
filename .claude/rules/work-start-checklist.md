# 작업 시작 체크리스트

이 레포에서 새 작업을 시작할 때 다음 순서로 컨텍스트를 잡는다.

1. **현재 단계 파악**
   - [docs/plans/13-roadmap.md](../../docs/plans/13-roadmap.md) 의 "진행 상황" 표 — 어디까지 ✅ 인지
   - 해당 Phase 의 작업 목록에서 다음 항목 확인
2. **관련 결정 확인**
   - [docs/plans/07-architecture-decision.md](../../docs/plans/07-architecture-decision.md) — ADR 7건.
     작업 주제와 겹치면 우선 참조. **결정과 어긋나는 구현을 하지 않는다**
3. **알려진 결함 확인**
   - [docs/plans/01-code-audit.md](../../docs/plans/01-code-audit.md) — 손대는 파일에 이미 알려진 결함이 있는지
   - 게임 규칙을 건드리면 [02-game-rules-audit.md](../../docs/plans/02-game-rules-audit.md) 대조표를 기준으로
4. **세부 룰 떠올리기**
   - [.claude/rules/](.) — 용어집·명명·커밋 규칙

## 작업 중

- 비자명한 결정이 생기면 `decisions-workflow.md` 를 따라 ADR 추가
- Phase 를 완료하면 로드맵의 진행 상황 표를 갱신 (자잘한 갱신은 모아서 한 번에)

## 코드를 고치기 전에

- **참조를 먼저 확인한다.** 삭제·리네임은 `grep -rn` 으로 사용처를 전부 확인한 뒤에
- 이미 맞게 동작하는 것은 **먼저 테스트로 고정**한 다음 리팩터링한다.
  그래야 무엇을 깨뜨렸는지 알 수 있다
