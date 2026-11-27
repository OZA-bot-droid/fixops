# K-apt 법정동 선택 데이터

기본 JSON은 동작 확인용 일부 서울 지역만 포함한다. 전국 자료는 공식 원본을 내려받아 생성한다. 코드 직접 입력은 항상 가능하다.

1. https://www.code.go.kr/stdcode/regCodeL.do 에서 법정동 코드 전체자료를 다운로드하고 압축을 해제한다.
2. 저장소 루트에서 `python scripts/generate-kapt-regions.py "법정동코드 전체자료.txt"` 실행.
3. 생성된 `frontend/public/data/kapt-regions.json`을 검토하여 커밋하고 frontend를 재빌드한다.
4. 폐지된 코드는 제외한다. 원본은 UTF-8 또는 CP949 탭 구분 파일(법정동코드, 법정동명, 폐지여부)이어야 한다.

코드 변환: 시도 2자리, 시군구 5자리, 법정동 10자리. K-apt API의 최신 지역코드 지원 여부는 별도로 검증한다.
