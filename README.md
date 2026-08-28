# AIBI

<p align="center">
  <img src="Artwork/aibi-icon.png" width="220" alt="AIBI 아이비 대표 아이콘">
</p>

AIBI(AI Browser Interface)는 사용자가 공식 AI 웹사이트에 직접 로그인한 세션을 앱 안에서 재사용해 프롬프트를 보내고, 변화하는 웹 결과를 안정적으로 가져와 앱에 돌려주는 공통 엔진입니다.

이 프로젝트가 AIBI의 기준 원본입니다. Codex 스킬은 이 프로젝트의 계약과 구현 자료를 읽어 대상 앱에 적용합니다. 특정 앱의 화면·문구·업무 규칙은 공통 엔진과 섞지 않고 `profiles/`에 둡니다.

## 결과적으로 나와야 하는 것

대상 앱에서 AIBI를 호출해 적용하면 다음 결과가 모두 있어야 합니다.

1. Gemini·ChatGPT·Claude별 로그인 상태가 `확인 중 / 로그인됨 / 로그인 필요` 중 하나로 짧고 정확하게 표시됩니다.
2. 로그인은 공식 페이지에서 사용자가 직접 하고, 앱은 비밀번호·토큰·쿠키 값을 복사하거나 저장하지 않습니다. 브라우저의 표준 영구 세션만 재사용합니다.
3. `브라우저 보기`가 꺼져 있으면 브라우저가 화면에 나타나지 않고 버튼 아래 진행 상태가 보입니다.
4. `브라우저 보기`가 켜져 있으면 입력·전송·생성 과정을 처음부터 볼 수 있습니다.
5. 숨김 실행 중 로그인·보안 확인·수동 조작이 필요할 때만 같은 작업을 보이는 브라우저로 안전하게 넘깁니다.
6. 텍스트와 지원 미디어를 제공자 규칙에 맞게 입력하고, 실제 전송 여부를 확인합니다.
7. 스트리밍 중간값이 아니라 완료되어 안정된 최종 결과만 가져옵니다.
8. 결과를 호스트 앱의 검증 규칙에 통과시킨 뒤 한 번만 반영하고 브라우저를 닫습니다.
9. 취소·시간 초과·페이지 변경·제공자 오류에서도 이전 작업의 늦은 콜백이 새 작업을 오염시키지 않습니다.
10. 제공자 화면 변경을 탐지하면 핵심 엔진을 뜯지 않고 해당 제공자 어댑터와 회귀 자료만 고쳐 적응할 수 있습니다.

## 디렉터리

- `docs/portable-contract.md`: 모든 앱이 지켜야 하는 공통 결과 계약
- `docs/provider-change-playbook.md`: AI 웹 화면 변경에 대응하는 절차
- `profiles/starmanager.md`: 스타매니저에만 해당하는 제품 요구
- `skill-source.md`: Codex AIBI 스킬이 따라야 하는 작업 순서와 경계

구현 자산과 플랫폼별 세부 자료는 현재 설치된 Codex 스킬 `/Users/armsone/.codex/skills/aibi`에 있으며, 이후 이 프로젝트의 `packages/`, `providers/`, `fixtures/`로 점진적으로 승격합니다.

## 이식 앱 업데이트

공통 참조 엔진은 `packages/`, 앱별 안전한 배포 단위는 `profiles/<host>/distribution/`, 설치 대상 allowlist는 `consumers/`에서 관리합니다. 현재 첫 소비자로 StarManager iOS·Android가 등록되어 있습니다.

```sh
python3 tools/aibi_sync.py status starmanager
python3 tools/aibi_sync.py apply starmanager
python3 tools/aibi_sync.py check starmanager
python3 tools/aibi_sync.py apply all
```

동기화는 마지막 설치 체크섬과 현재 앱 파일이 일치할 때만 갱신합니다. 앱에서 따로 고친 파일은 절대 덮어쓰지 않고 충돌로 중단합니다. 자세한 절차는 `docs/distribution-and-updates.md`를 따릅니다.
