# 건축HUB 기반 동·호수 등록 (초기 구현)

공식 서비스: https://www.data.go.kr/data/15134735/openapi.do
전유부 API: getBrExposInfo. 시군구 5자리, 법정동 뒤 5자리, 대지구분, 본번/부번 4자리, _type=json.
K-apt 단지 등록과 기존 명부 인증은 유지한다. Asset ADMIN만 관리권 범위에서 조회/등록한다.
K-apt 공식 지번 후보 자동 입력을 지원하되 관리자 확인이 필요하다. 관리자가 공식 지번을 확인하여 입력하고 단지 주소 및 후보 목록을 검토한다. 여러 필지는 각각 조회한다. 필지 내 여러 단지가 있을 수 있으므로 자동 전체 등록 금지. 전유부 API 단독 사용. bldNm/dongNm/hoNm의 비주거 키워드를 우선 검사하고, 검토 키워드 및 집합건축물/전유부 코드와 숫자형 동·호수를 검증해 주거 후보를 분류한다. 공식 용도 확정은 아니며 관리자 확인 필수. 비주거 및 검토 필요 항목은 자동 등록하지 않고 사유를 미리보기에 제공한다. 누락·결번·혼합용도는 기존 수동 위치 등록으로 보완한다.
기존 COMPLEX 루트를 선택한 후 검토한 전유부 PK 목록만 등록한다. 서버는 공식 API를 다시 조회한다. 같은 활성 동·호는 유지하고 비활성/타입 충돌은 전체 롤백한다. 동·호 코드의 끝 '동'/'호'만 제거하며 실제 명부 코드와 일치해야 한다. 입주민 이름/연락처를 조회하거나 Asset에 전송하지 않는다.
BUILDING_REGISTER_SERVICE_KEY는 별도 승인된 키를 사용할 수 있으며 Docker에서는 빈 값일 때 KAPT_SERVICE_KEY를 재사용한다. 실제 API 이용 승인 및 JSON 응답·페이지 크기 검증 필요. 조회 최대 35초, 100페이지, 2MiB/page, 인스턴스 동시 2회. 실제 외부 쿼터 제한 및 분산 rate limit, 복수 필지 자동 매핑, 공식 PK 영속 매핑, 명부 불일치 미리보기는 후속 작업이다.

## K-apt parcel hint
Member GET /api/organizations/{id}/complexes/{complexId}/building-register-hint resolves the approved ADMIN scope and stored K-apt code, then retrieves official detail. The response suggests parcel parameters only for a recognizable single lot after a dong/ri name. No inference from road numbers. Manual review and explicit import confirmation remain mandatory; this is not proof of cadastral identity or full multi-parcel coverage.

## 후속 검증
K-apt 공식 세대수와 후보 수 비교는 별도 후속 작업이다. 등록 확정 시 서버는 전유부 API를 재조회하고 동일한 후보 분류를 적용한다. 특수 동·호수는 수동 등록으로 보완한다.

## 등록 UI 개선
활성 COMPLEX 루트가 하나면 자동 선택한다. 복수 루트는 직접 선택한다. 주거 후보는 동/호 자연 정렬, 동별 필터 및 전체/동별 선택을 제공한다. 목록 내부 스크롤과 하단 등록 영역으로 확인 조건·선택 수·처리 상태를 표시한다. 등록 성공 시 신규 동/세대와 기존 유지 건수 모달 및 위치 갱신. DB 등록 여부는 서버 성공 응답 기준이다. 루트 및 후보 검증은 기존 서버 정책을 유지한다.
