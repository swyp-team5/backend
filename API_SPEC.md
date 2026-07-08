# AutoSchedule API 명세서

## 1. 문서 범위

이 문서는 현재 구현된 AutoSchedule 백엔드 API를 기준으로 작성한다.

현재 구현 범위는 다음과 같다.

- 소셜 로그인
- 회원가입 약관 조회
- 사장님 회원가입
- 근무자 회원가입
- Access Token / Refresh Token 재발급
- 로그아웃
- 회원탈퇴 신청
- 회원탈퇴 취소
- 내 프로필 조회
- 내 프로필 수정
- 프로필 이미지 S3 업로드 URL 발급
- 프로필 이미지 S3 직접 업로드
- 프로필 이미지 업로드 확정
- 프로필 이미지 수정/교체
- 프로필 이미지 삭제
- 내 사업장 목록 조회
- 사업장 추가
- 사업장 전화번호 추가/수정/삭제
- 사업장 크루 초대 코드 생성
- 사업장 크루 초대 코드 수락
- 사업장 크루 초대 코드 이력 조회
- 사업장 근무자 목록 조회
- 사업장 근무자 삭제
- 공지 이미지 S3 업로드 URL 발급
- 공지 이미지 S3 직접 업로드
- 공지 작성 시 이미지 첨부
- 공지 조회 시 이미지 메타데이터 조회
- 사업장 공지사항 작성
- 사업장 공지사항 목록 조회
- 사업장 대표 공지 조회
- 공지사항 단건 조회
- 홈 대표 공지 조회
- 홈 최신 공지 조회
- 사업장 공지사항 수정
- 사업장 공지사항 삭제
- 사업장 공지사항 댓글 작성
- 사업장 공지사항 댓글 조회
- 사업장 공지사항 댓글 수정
- 사업장 공지사항 댓글 삭제
- 사업장 공지사항 공감 선택/변경/취소
- FCM 토큰 등록 또는 갱신
- FCM 토큰 비활성화
- FCM 푸시 수신 설정 조회/변경
- 알림함 조회
- 알림 단건 읽음 처리
- 모든 알림 읽음 처리
- FCM 테스트 푸시 발송
- 스케줄 조건 생성
- 달력 활성화 일자 조회
- 특정 일자 타임 상세조회
- 최근 스케줄 조건 조회
- 스케줄 조건 초기화
- 근무자 근무불가 시간 선택
- 근무자 제출 여부 조회
- 사업장 근무자 근무 불가 제출 현황 조회
- 자동 스케줄 생성
- 자동 스케줄 재생성
- 자동 스케줄 미리보기 조회
- 주간 스케줄 확정
- 확정 스케줄 단건 근무 파트 추가
- 확정 스케줄 단건 근무 파트 수정
- 확정 스케줄 단건 근무 파트 삭제
- 근무자 본인 확정 스케줄 달력 조회
- 사장용 사업장 기간 확정 스케줄 조회
- 사장용 사업장 주간 확정 스케줄 조회
- 대타 요청 생성
- 교대 요청 생성
- 교대/대타 요청 목록 조회
- 교대/대타 대상 근무자 수락/거절
- 교대/대타 요청자 취소
- 교대/대타 사장 최종 승인/거절

모든 API URL은 `/api/*` 규칙을 따른다. `/api/v1/*` 형식은 사용하지 않는다.

## 2. 공통 규칙

### 2.1 Base URL

```text
https://chackchack.shop
```

로컬 개발 환경에서 직접 서버를 실행하거나 Docker Compose 로컬 패키지를 사용할 때만
`http://localhost:{APP_PORT}` 형식을 사용한다.

### 2.2 Content-Type

요청 본문이 있는 API는 JSON을 사용한다.

```http
Content-Type: application/json
```

### 2.3 인증 방식

인증이 필요한 API는 HTTP Authorization 헤더에 Bearer access token을 전달한다.

```http
Authorization: Bearer {accessToken}
```

`/api/auth/**` API와 `GET /api/terms/signup` API는 인증 없이 호출할 수 있다.

### 2.4 지원 소셜 제공자

```text
GOOGLE
KAKAO
APPLE
```

### 2.5 기기 플랫폼

```text
ANDROID
IOS
```

### 2.6 회원 역할

```text
OWNER
WORKER
```

### 2.7 회원 상태

```text
ACTIVE
WITHDRAWAL_PENDING
WITHDRAWN
```

현재 회원가입 직후 상태는 `ACTIVE`이다.

- `WITHDRAWAL_PENDING`: 회원탈퇴 신청 후 30일 유예 상태. 유예 기간 안에는 로그인 후 탈퇴 취소 API로 복구할 수 있다.
- `WITHDRAWN`: 영구 탈퇴 완료 상태. 현재 개인정보 영구 삭제 배치는 미구현이며 추후 관리자 기능에서 처리한다.

### 2.8 매장 규모

```text
ONE_TO_FOUR
FIVE_TO_NINE
TEN_TO_SEVENTEEN
EIGHTEEN_TO_TWENTY_THREE
```

## 3. 공통 응답

### 3.1 로그인 성공 응답

기존 회원 로그인, 회원가입 성공, refresh token 재발급 성공 시 동일한 응답 형식을 사용한다.

```json
{
  "status": "LOGIN_SUCCESS",
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 1800,
  "refreshTokenExpiresIn": 1209600,
  "member": {
    "memberId": 1,
    "name": "정진섭",
    "role": "OWNER",
    "status": "ACTIVE"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| status | string | `LOGIN_SUCCESS` |
| accessToken | string | 우리 서버 인증/인가용 access token |
| refreshToken | string | 우리 서버 refresh token |
| tokenType | string | 현재 `Bearer` 고정 |
| accessTokenExpiresIn | number | access token 만료 시간, 초 단위 |
| refreshTokenExpiresIn | number | refresh token 만료 시간, 초 단위 |
| member.memberId | number | 회원 ID |
| member.name | string | 회원 이름 |
| member.role | string | `OWNER`, `WORKER` |
| member.status | string | 회원 상태 |

### 3.2 회원가입 필요 응답

소셜 인증은 성공했지만 DB에 가입 완료 회원이 없을 때 반환한다.

```json
{
  "status": "SIGNUP_REQUIRED"
}
```

이 응답에서는 회원 데이터가 생성되지 않는다. 클라이언트는 회원가입 화면으로 이동해야 하며, 회원가입 API 호출 시 소셜 인증 정보를 다시 전달해야 한다.

### 3.3 에러 응답

모든 API 에러는 아래 형식을 사용한다.

```json
{
  "code": "4002",
  "message": "인증이 필요합니다.",
  "errors": [],
  "path": "/api/auth/social-login",
  "timestamp": "2026-06-12T01:00:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| code | string | 서비스 에러 코드 |
| message | string | 사용자 또는 개발자가 이해할 수 있는 에러 메시지 |
| errors | array | validation field error 목록 |
| path | string | 요청 경로 |
| timestamp | string | 에러 발생 시각 |

Validation 실패 시 `errors`에 field 단위 메시지가 포함된다.

### 3.4 에러 코드

| HTTP Status | Code | 의미 |
| ---: | --- | --- |
| 400 | 4000 | Validation 실패 |
| 400 | 4001 | 잘못된 요청 |
| 401 | 4002 | 인증 실패 또는 유효하지 않은 토큰 |
| 403 | 4003 | 접근 권한 없음 |
| 404 | 4004 | 리소스 없음 |
| 409 | 4005 | 현재 상태와 충돌 |
| 500 | 500 | 서버 내부 오류 |

## 4. 소셜 인증 토큰 규칙

### 4.1 Google

요청자는 `idToken`을 전달한다.

서버 검증 규칙은 다음과 같다.

- Google ID Token 서명 검증
- `aud`가 서버에 등록된 Google Client ID 목록 중 하나와 일치하는지 검증
- issuer 검증
- 만료 시간 검증
- `sub`를 `social_subject`로 사용
- 이메일이 있으면 `social_email`로 저장

`accessToken`, `authorizationCode`를 함께 보내도 Google 인증에는 사용하지 않는다.

### 4.2 Kakao

요청자는 `accessToken`을 전달한다.

서버 검증 규칙은 다음과 같다.

- Kakao 사용자 정보 API를 access token으로 호출
- Kakao 사용자 `id`를 `social_subject`로 사용
- `kakao_account.email`이 있으면 `social_email`로 저장
- 이메일이 없어도 허용

`idToken`, `authorizationCode`를 함께 보내도 Kakao 인증에는 사용하지 않는다.

### 4.3 Apple

요청자는 `idToken`과 `authorizationCode`를 모두 전달한다.

서버 검증 규칙은 다음과 같다.

- Apple identity token RS256 서명 검증
- Apple JWKS 공개키 조회
- `iss` 검증
- `aud`가 서버에 등록된 Apple Client ID와 일치하는지 검증
- 만료 시간 검증
- `sub` 검증
- Apple token endpoint를 통해 `authorizationCode` 검증
- identity token의 `sub`와 authorization code 교환 결과의 `sub` 일치 검증
- 이메일이 있으면 `social_email`로 저장
- 이메일이 없어도 허용

`accessToken`을 함께 보내도 Apple 인증에는 사용하지 않는다.

## 5. 약관 조회 API

### 5.1 회원가입 약관 조회

회원가입 전에 호출하는 공개 API다. 클라이언트가 약관 유형을 직접 조합하지 않고 서버가 회원가입 역할 기준으로 필요한 활성 약관을 조합해 반환한다.

```http
GET /api/terms/signup?role=OWNER
GET /api/terms/signup?role=WORKER
```

#### 요청 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| role | string | Y | 회원가입 역할. `OWNER`, `WORKER` |

#### 역할별 반환 약관

| role | 반환 약관 유형 |
| --- | --- |
| OWNER | `COMMON`, `OWNER` |
| WORKER | `COMMON`, `WORKER` |

#### 성공 응답

```http
200 OK
```

```json
{
  "terms": [
    {
      "termsId": 1,
      "termsType": "COMMON",
      "title": "만 14세 이상입니다.",
      "required": true,
      "version": "1.0.0",
      "content": "약관 본문"
    },
    {
      "termsId": 5,
      "termsType": "OWNER",
      "title": "개인정보보호의무",
      "required": true,
      "version": "1.0.0",
      "content": "약관 본문"
    }
  ]
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| terms | array | 약관 목록 |
| terms[].termsId | number | 약관 ID |
| terms[].termsType | string | 약관 유형. `COMMON`, `OWNER`, `WORKER` |
| terms[].title | string | 약관 제목 |
| terms[].required | boolean | 필수 동의 여부 |
| terms[].version | string | 약관 버전 |
| terms[].content | string | 약관 본문 |

#### 검증 규칙

- `status = ACTIVE`인 약관만 반환한다.
- 응답 정렬은 `termsType`, `termsId` 오름차순이다.
- `role`이 없으면 400으로 응답한다.
- `OWNER`, `WORKER`가 아닌 role 값은 400으로 응답한다.

## 6. 인증 API

### 6.1 소셜 로그인

기존 회원 로그인 여부를 확인한다.

```http
POST /api/auth/social-login
```

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| provider | string | Y | `GOOGLE`, `KAKAO`, `APPLE` |
| idToken | string | provider별 | Google 필수, Apple 필수 |
| accessToken | string | provider별 | Kakao 필수 |
| authorizationCode | string | provider별 | Apple 필수 |
| device | object | Y | 로그인 기기 정보 |
| device.deviceId | string | Y | 클라이언트가 생성한 기기 고유 ID |
| device.platform | string | Y | `ANDROID`, `IOS` |
| device.appVersion | string | Y | 앱 버전 |

#### Google 요청 예시

```json
{
  "provider": "GOOGLE",
  "idToken": "google-id-token",
  "device": {
    "deviceId": "device-uuid",
    "platform": "ANDROID",
    "appVersion": "1.0.0"
  }
}
```

#### Kakao 요청 예시

```json
{
  "provider": "KAKAO",
  "accessToken": "kakao-access-token",
  "device": {
    "deviceId": "device-uuid",
    "platform": "IOS",
    "appVersion": "1.0.0"
  }
}
```

#### Apple 요청 예시

```json
{
  "provider": "APPLE",
  "idToken": "apple-id-token",
  "authorizationCode": "apple-authorization-code",
  "device": {
    "deviceId": "device-uuid",
    "platform": "IOS",
    "appVersion": "1.0.0"
  }
}
```

#### 성공 응답: 기존 회원

```http
200 OK
```

로그인 성공 응답 형식을 반환한다.

- 기존 회원 상태가 `ACTIVE`이면 정상 로그인 처리한다.
- 기존 회원 상태가 `WITHDRAWAL_PENDING`이고 탈퇴 신청 후 30일 이내이면 로그인은 허용하되 상태를 자동 복구하지 않는다.
- 기존 회원 상태가 `WITHDRAWAL_PENDING`이고 30일이 지났으면 로그인할 수 없다.

#### 성공 응답: 신규 소셜 사용자

```http
200 OK
```

```json
{
  "status": "SIGNUP_REQUIRED"
}
```

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | `provider`, `device` 등 필수 필드 누락 |
| 400 | 4001 | provider별 필수 토큰 누락 |
| 401 | 4002 | 소셜 토큰 검증 실패 |
| 409 | 4005 | 탈퇴 취소 가능 기간이 지난 회원 또는 영구 탈퇴 완료 회원 |
| 500 | 500 | 소셜 로그인 설정 누락 |

### 6.2 사장님 회원가입

소셜 인증 정보를 다시 검증한 뒤 사장님 회원가입을 완료한다.

회원가입 성공 시 다음 데이터를 생성한다.

- `member`
- `member_terms_agreement`
- `work_place`
- `crew` OWNER 소속
- Redis refresh token session

```http
POST /api/auth/signup/owner
```

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| provider | string | Y | `GOOGLE`, `KAKAO`, `APPLE` |
| idToken | string | provider별 | Google 필수, Apple 필수 |
| accessToken | string | provider별 | Kakao 필수 |
| authorizationCode | string | provider별 | Apple 필수 |
| name | string | Y | 최대 10자 |
| phoneNumber | string | Y | 하이픈 없는 11자리 숫자, 예: `01000000000` |
| termsAgreements | array | Y | 약관 동의 목록 |
| termsAgreements[].termsId | number | Y | 약관 ID |
| termsAgreements[].agreed | boolean | Y | 동의 여부 |
| workPlace | object | Y | 최초 사업장 정보 |
| workPlace.size | string | Y | 매장 규모 |
| workPlace.name | string | Y | 사업장 이름 |
| workPlace.roadAddress | string | Y | 도로명 주소 |
| workPlace.detailAddress | string | N | 상세 주소 |
| workPlace.phoneNumber | string/null | N | 매장 전화번호. 부가 정보이며 하이픈 없는 8~11자리 숫자 |
| device | object | Y | 로그인 기기 정보 |

#### 요청 예시

```json
{
  "provider": "GOOGLE",
  "idToken": "google-id-token",
  "name": "정진섭",
  "phoneNumber": "01000000000",
  "termsAgreements": [
    { "termsId": 1, "agreed": true },
    { "termsId": 2, "agreed": true },
    { "termsId": 3, "agreed": true },
    { "termsId": 4, "agreed": false },
    { "termsId": 5, "agreed": true }
  ],
  "workPlace": {
    "size": "FIVE_TO_NINE",
    "name": "스위프",
    "roadAddress": "서울시 강남구 테헤란로",
    "detailAddress": "3층",
    "phoneNumber": null
  },
  "device": {
    "deviceId": "device-uuid",
    "platform": "ANDROID",
    "appVersion": "1.0.0"
  }
}
```

#### 성공 응답

```http
201 Created
```

로그인 성공 응답 형식을 반환한다.

#### 주요 비즈니스 규칙

- 동일 `provider + social_subject`로 이미 가입된 회원이 있으면 중복 가입으로 거절한다.
- `COMMON`, `OWNER` 타입의 활성 필수 약관은 모두 `agreed: true`여야 한다.
- 요청한 `termsId`가 존재하지 않으면 거절한다.
- 회원가입 성공 시 자동 로그인 처리하고 access token, refresh token을 발급한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 요청 DTO validation 실패 |
| 400 | 4001 | provider별 필수 토큰 누락, 필수 약관 미동의, 존재하지 않는 약관 |
| 401 | 4002 | 소셜 토큰 검증 실패 |
| 409 | 4005 | 이미 가입된 소셜 계정 |
| 500 | 500 | 소셜 로그인 설정 누락 |

### 6.3 근무자 회원가입

소셜 인증 정보를 다시 검증한 뒤 근무자 회원가입을 완료한다.

회원가입 성공 시 다음 데이터를 생성한다.

- `member`
- `member_terms_agreement`
- Redis refresh token session

```http
POST /api/auth/signup/worker
```

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| provider | string | Y | `GOOGLE`, `KAKAO`, `APPLE` |
| idToken | string | provider별 | Google 필수, Apple 필수 |
| accessToken | string | provider별 | Kakao 필수 |
| authorizationCode | string | provider별 | Apple 필수 |
| name | string | Y | 최대 10자 |
| phoneNumber | string | Y | 하이픈 없는 11자리 숫자, 예: `01000000000` |
| termsAgreements | array | Y | 약관 동의 목록 |
| termsAgreements[].termsId | number | Y | 약관 ID |
| termsAgreements[].agreed | boolean | Y | 동의 여부 |
| device | object | Y | 로그인 기기 정보 |

#### 요청 예시

```json
{
  "provider": "KAKAO",
  "accessToken": "kakao-access-token",
  "name": "정진섭",
  "phoneNumber": "01000000000",
  "termsAgreements": [
    { "termsId": 1, "agreed": true },
    { "termsId": 2, "agreed": true },
    { "termsId": 3, "agreed": true },
    { "termsId": 4, "agreed": false }
  ],
  "device": {
    "deviceId": "device-uuid",
    "platform": "IOS",
    "appVersion": "1.0.0"
  }
}
```

#### 성공 응답

```http
201 Created
```

로그인 성공 응답 형식을 반환한다.

#### 주요 비즈니스 규칙

- 동일 `provider + social_subject`로 이미 가입된 회원이 있으면 중복 가입으로 거절한다.
- `COMMON` 타입의 활성 필수 약관은 모두 `agreed: true`여야 한다.
- 요청한 `termsId`가 존재하지 않으면 거절한다.
- 회원가입 성공 시 자동 로그인 처리하고 access token, refresh token을 발급한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 요청 DTO validation 실패 |
| 400 | 4001 | provider별 필수 토큰 누락, 필수 약관 미동의, 존재하지 않는 약관 |
| 401 | 4002 | 소셜 토큰 검증 실패 |
| 409 | 4005 | 이미 가입된 소셜 계정 |
| 500 | 500 | 소셜 로그인 설정 누락 |

### 6.4 토큰 재발급

Refresh token을 검증하고 access token과 refresh token을 새로 발급한다.

```http
POST /api/auth/token/refresh
```

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| refreshToken | string | Y | 기존 refresh token |
| deviceId | string | Y | refresh token을 발급한 기기 ID |

#### 요청 예시

```json
{
  "refreshToken": "refresh-token",
  "deviceId": "device-uuid"
}
```

#### 성공 응답

```http
200 OK
```

로그인 성공 응답 형식을 반환한다.

#### 주요 비즈니스 규칙

- refresh token JWT 서명과 만료 시간을 검증한다.
- refresh token 타입 claim이 `REFRESH`인지 검증한다.
- Redis에 저장된 `memberId + deviceId` 기준 token hash와 요청 token hash가 일치해야 한다.
- 성공 시 refresh token을 회전한다.
- 이전 refresh token은 재사용할 수 없다.
- 다른 `deviceId`로 발급한 refresh token은 사용할 수 없다.
- 회원 상태가 `ACTIVE`가 아니면 재발급할 수 없다.

#### Redis 저장 규칙

```text
key: auth:refresh-token:{memberId}:{deviceId}
value: sha256(refreshToken)
ttl: refreshTokenExpiresIn
```

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | `refreshToken`, `deviceId` 누락 |
| 401 | 4002 | refresh token 만료, 서명 오류, Redis 세션 없음, hash 불일치, 비활성 회원 |

### 6.5 로그아웃

현재 기기의 refresh token 세션을 삭제한다.

```http
POST /api/auth/logout
```

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| refreshToken | string | Y | 로그아웃할 refresh token |
| deviceId | string | Y | 로그아웃할 기기 ID |

#### 요청 예시

```json
{
  "refreshToken": "refresh-token",
  "deviceId": "device-uuid"
}
```

#### 성공 응답

```http
204 No Content
```

응답 본문은 없다.

#### 주요 비즈니스 규칙

- refresh token JWT 서명과 만료 시간을 검증한다.
- Redis에 저장된 `memberId + deviceId` 기준 token hash와 요청 token hash가 일치해야 한다.
- hash가 일치할 때만 Redis refresh token 세션을 삭제한다.
- 이미 회전된 예전 refresh token으로는 현재 세션을 삭제할 수 없다.
- 로그아웃은 refresh token 세션만 삭제한다.
- 이미 발급된 access token은 stateless 특성상 만료 전까지 유효하다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | `refreshToken`, `deviceId` 누락 |
| 401 | 4002 | refresh token 만료, 서명 오류, Redis 세션 없음, hash 불일치, 비활성 회원 |

### 6.6 회원탈퇴 신청

로그인한 회원 본인의 탈퇴를 신청한다. 탈퇴 신청 즉시 영구 삭제하지 않고 30일 유예 상태로 전환한다.

```http
DELETE /api/members/me
```

#### 인증

```http
Authorization: Bearer {ACCESS_TOKEN}
```

#### 성공 응답

```http
204 No Content
```

응답 본문은 없다.

#### 주요 비즈니스 규칙

- `ACTIVE` 회원은 `WITHDRAWAL_PENDING` 상태로 변경된다.
- `member.deleted_at`에는 탈퇴 신청 시각을 저장한다.
- 이미 `WITHDRAWAL_PENDING` 상태이면 멱등하게 성공하며 최초 `deleted_at`은 유지한다.
- 회원의 모든 기기 refresh token 세션을 Redis에서 제거한다.
- 회원의 모든 활성 FCM token은 `INACTIVE`로 비활성화한다.
- 이미 발급된 access token은 stateless JWT 특성상 서버에서 물리 폐기하지 않는다. 클라이언트는 탈퇴 성공 즉시 로컬 토큰을 삭제해야 한다.
- 30일 이후 개인정보 영구 삭제는 아직 수행하지 않는다. 추후 관리자 기능에서 물리 삭제로 구현한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 401 | 4002 | access token 누락, 만료, 서명 오류 |
| 409 | 4005 | 이미 영구 탈퇴 완료된 회원 |

### 6.7 회원탈퇴 취소

30일 유예 기간 안에 로그인한 회원이 본인의 탈퇴 신청을 취소한다. 관리자 검토 없이 즉시 정상 상태로 복구한다.

```http
POST /api/members/me/withdrawal-cancel
```

#### 인증

```http
Authorization: Bearer {ACCESS_TOKEN}
```

#### 성공 응답

```http
204 No Content
```

응답 본문은 없다.

#### 주요 비즈니스 규칙

- `WITHDRAWAL_PENDING` 회원이 30일 유예 기간 안에 호출하면 `ACTIVE`로 복구된다.
- 복구 시 `member.deleted_at`은 `null`로 초기화한다.
- 이미 `ACTIVE` 상태이면 멱등하게 성공한다.
- 유예 기간 내 로그인은 가능하지만 자동 복구하지 않는다. 클라이언트는 회원 상태가 `WITHDRAWAL_PENDING`이면 탈퇴 취소 화면으로 이동해야 한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 401 | 4002 | access token 누락, 만료, 서명 오류 |
| 409 | 4005 | 탈퇴 취소 가능 기간 경과, 이미 영구 탈퇴 완료된 회원 |

## 6.8 사업장 API

### 6.8.1 홈 화면 매장 드롭다운 목록 조회

홈 화면 상단의 매장 선택 드롭다운에서 사용할 현재 로그인 회원의 사업장 목록을 조회한다.
사장님과 근무자는 여러 매장에 속할 수 있으므로, 앱은 이 API로 선택 가능한 매장 목록을 먼저 조회한 뒤 선택된 `workPlaceId` 기준으로 공지, 스케줄, 출퇴근 등 홈 데이터를 조회한다.

```http
GET /api/work-places/me
Authorization: Bearer {accessToken}
```

#### 인증

```text
OWNER, WORKER
```

#### 성공 응답

```json
{
  "workPlaces": [
    {
      "workPlaceId": 1,
      "name": "매장명1",
      "size": "FIVE_TO_NINE",
      "roadAddress": "서울시 강남구 ...",
      "detailAddress": "3층",
      "phoneNumber": "0212345678",
      "ownerMemberId": 1,
      "crewId": 10,
      "crewRole": "OWNER",
      "joinStatus": "APPROVED",
      "crewStatus": "ACTIVE",
      "workPlaceStatus": "ACTIVE"
    }
  ]
}
```

#### 홈 화면 사용 흐름

1. 앱 진입 후 access token으로 `GET /api/work-places/me`를 호출한다.
2. `workPlaces` 배열을 홈 화면 매장 드롭다운 옵션으로 렌더링한다.
3. 사용자가 매장을 선택하면 해당 항목의 `workPlaceId`를 현재 선택 매장 ID로 저장한다.
4. 선택된 `workPlaceId`로 매장별 홈 데이터를 조회한다.

```http
GET /api/home/work-places/{workPlaceId}/representative-notice
GET /api/home/work-places/{workPlaceId}/latest-notice
GET /api/work-places/{workPlaceId}/notices?page=0&size=20
```

5. 사용자가 드롭다운에서 다른 매장을 선택하면 새 `workPlaceId`로 홈 데이터를 다시 조회한다.

#### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| workPlaces | array | 현재 회원이 홈 화면 드롭다운에서 선택할 수 있는 사업장 목록 |
| workPlaces[].workPlaceId | number | 사업장 ID. 드롭다운 선택 후 매장별 API 호출에 사용하는 기준값 |
| workPlaces[].name | string | 사업장 이름. 드롭다운에 노출할 매장명 |
| workPlaces[].size | string | 매장 규모 |
| workPlaces[].roadAddress | string | 도로명 주소 |
| workPlaces[].detailAddress | string/null | 상세 주소 |
| workPlaces[].phoneNumber | string/null | 매장 전화번호. 부가 정보이므로 없으면 `null` |
| workPlaces[].ownerMemberId | number | 사업장을 생성한 사장님 회원 ID |
| workPlaces[].crewId | number | 현재 회원의 해당 사업장 crew ID |
| workPlaces[].crewRole | string | 현재 회원의 해당 사업장 역할. `OWNER`, `WORKER` |
| workPlaces[].joinStatus | string | 현재 회원의 해당 사업장 가입 상태. 현재 응답에는 `APPROVED`만 포함 |
| workPlaces[].crewStatus | string | 현재 회원의 해당 사업장 소속 상태. 현재 응답에는 `ACTIVE`만 포함 |
| workPlaces[].workPlaceStatus | string | 사업장 상태. 현재 응답에는 `ACTIVE`만 포함 |

#### 주요 비즈니스 규칙

- OWNER, WORKER 모두 같은 API를 사용한다.
- 이 API는 홈 화면의 매장 드롭다운 옵션을 구성하기 위한 API다.
- 회원이 `crew`에서 `APPROVED / ACTIVE` 상태로 소속된 활성 사업장만 반환한다.
- `work_place.status = ACTIVE`, `work_place.deleted_at is null`인 사업장만 반환한다.
- 사장님도 `crew_role = OWNER` 소속 기준으로 조회한다.
- 근무자는 여러 사업장에 소속될 수 있으며, 승인된 활성 소속만 반환한다.
- 소속된 활성 사업장이 없으면 빈 배열을 반환한다.
- 정렬 기준은 `workPlaceId ASC`다.
- 서버는 기본 선택 매장을 별도로 판단하지 않는다. 클라이언트는 보통 `workPlaces[0]`을 초기 선택값으로 사용하거나, 로컬에 마지막 선택 매장이 있으면 그 값을 우선 사용할 수 있다.
- 공지사항은 매장별 데이터이므로 선택된 `workPlaceId`가 바뀌면 공지 관련 API도 다시 호출해야 한다.
- 서버는 `crew`와 `work_place`를 fetch join으로 한 번에 조회하여 매장 목록 조회에서 N+1 쿼리를 발생시키지 않는다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |

### 6.8.2 사업장 추가

로그인한 사장님이 추가 사업장을 생성한다. 생성 성공 시 해당 사장님은 새 사업장의 `OWNER` 크루로 즉시 등록된다.

```http
POST /api/work-places
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| size | string | Y | 매장 규모. `ONE_TO_FOUR`, `FIVE_TO_NINE`, `TEN_TO_SEVENTEEN`, `EIGHTEEN_TO_TWENTY_THREE` |
| name | string | Y | 사업장 이름. 최대 100자 |
| roadAddress | string | Y | 도로명 주소. 최대 255자 |
| detailAddress | string/null | N | 상세 주소. 최대 100자 |
| phoneNumber | string/null | N | 매장 전화번호. 부가 정보이며 하이픈 없는 8~11자리 숫자 |

#### 요청 예시

```json
{
  "size": "FIVE_TO_NINE",
  "name": "스위프 2호점",
  "roadAddress": "서울시 강남구 테헤란로 1",
  "detailAddress": "3층",
  "phoneNumber": "0212345678"
}
```

전화번호를 아직 입력하지 않는 경우 `phoneNumber`를 생략하거나 `null`로 전달할 수 있다.

```json
{
  "size": "ONE_TO_FOUR",
  "name": "전화번호 없는 매장",
  "roadAddress": "서울시 강남구 테헤란로 2",
  "detailAddress": null,
  "phoneNumber": null
}
```

#### 성공 응답

```http
201 Created
```

```json
{
  "workPlaceId": 2,
  "name": "스위프 2호점",
  "size": "FIVE_TO_NINE",
  "roadAddress": "서울시 강남구 테헤란로 1",
  "detailAddress": "3층",
  "phoneNumber": "0212345678",
  "ownerMemberId": 1,
  "crewId": 15,
  "crewRole": "OWNER",
  "joinStatus": "APPROVED",
  "crewStatus": "ACTIVE",
  "workPlaceStatus": "ACTIVE"
}
```

#### 주요 비즈니스 규칙

- 사장님 계정만 사업장을 추가할 수 있다.
- 사업장 생성과 OWNER 크루 생성은 같은 트랜잭션에서 처리한다.
- 회원가입 시 최초 사업장 생성과 로그인 후 추가 사업장 생성은 같은 내부 생성 정책을 사용한다.
- `phoneNumber`는 부가 정보이므로 `null`을 허용한다.
- `phoneNumber`를 입력하는 경우 하이픈 없는 숫자 8~11자리만 허용한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 필수값 누락, 길이 초과, 전화번호 형식 오류 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |

### 6.8.3 사업장 전화번호 추가/수정/삭제

사장님이 본인 소유 사업장의 전화번호 부가 정보를 추가, 수정 또는 삭제한다.

```http
PATCH /api/work-places/{workPlaceId}/phone-number
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 전화번호를 수정할 사업장 ID |

#### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string/null | N | 매장 전화번호. `null`이면 기존 전화번호를 삭제한다. 입력 시 하이픈 없는 8~11자리 숫자 |

#### 요청 예시: 추가/수정

```json
{
  "phoneNumber": "15881234"
}
```

#### 요청 예시: 삭제

```json
{
  "phoneNumber": null
}
```

#### 성공 응답

```http
200 OK
```

```json
{
  "workPlaceId": 2,
  "name": "스위프 2호점",
  "size": "FIVE_TO_NINE",
  "roadAddress": "서울시 강남구 테헤란로 1",
  "detailAddress": "3층",
  "phoneNumber": "15881234",
  "ownerMemberId": 1,
  "crewId": 15,
  "crewRole": "OWNER",
  "joinStatus": "APPROVED",
  "crewStatus": "ACTIVE",
  "workPlaceStatus": "ACTIVE"
}
```

#### 주요 비즈니스 규칙

- 사장님만 수정할 수 있다.
- 사장님 본인이 소유한 활성 사업장의 전화번호만 수정할 수 있다.
- 전화번호 삭제는 `phoneNumber: null`로 요청한다.
- 전체 사업장 정보 수정 API는 아직 제공하지 않는다. 현재는 전화번호 부가 정보만 별도 수정한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 전화번호 형식 오류 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |
| 404 | 4004 | 본인 소유 활성 사업장을 찾을 수 없음 |

## 7. 크루 초대 API

### 7.1 사업장 크루 초대 코드 생성

사장님이 본인 소유 사업장에 근무자를 초대하기 위한 1회용 6자리 초대 코드를 생성한다.

```http
POST /api/work-places/{workPlaceId}/crew-invitations
```

#### 인증

```http
Authorization: Bearer {OWNER_ACCESS_TOKEN}
```

`OWNER` 권한 회원만 호출할 수 있다.

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 초대 코드를 생성할 사업장 ID |

#### 요청 본문

없음

#### 성공 응답

```http
201 Created
```

```json
{
  "invitationId": 1,
  "workPlaceId": 10,
  "inviteCode": "839204",
  "inviteUrl": "chack-chack://crew-invitations/839204",
  "expiresAt": "2026-06-13T15:00:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| invitationId | number | 크루 초대 ID |
| workPlaceId | number | 사업장 ID |
| inviteCode | string | 6자리 숫자 초대 코드 |
| inviteUrl | string | 앱 딥링크 초대 URL |
| expiresAt | string | 초대 코드 만료 시각 |

#### 주요 비즈니스 규칙

- 사장님만 초대 코드를 생성할 수 있다.
- 사장님 본인이 소유한 활성 사업장에 대해서만 생성할 수 있다.
- 초대 코드는 6자리 숫자다.
- 초대 코드는 1회용이다.
- 초대 코드는 1시간 동안 유효하다.
- 초대 코드와 실패 횟수 제한은 Redis에 저장한다.

#### Redis 저장 규칙

```text
key: crew-invitation:{inviteCode}
value: {invitationId}
ttl: 1 hour
```

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |
| 404 | 4004 | 초대할 수 있는 사업장을 찾을 수 없음 |
| 500 | 500 | 초대 코드 생성 실패 |

### 7.2 사업장 크루 초대 코드 수락

근무자가 초대 코드를 수락하면 해당 사업장 크루로 즉시 승인 등록된다.

```http
POST /api/crew-invitations/{inviteCode}/accept
```

#### 인증

```http
Authorization: Bearer {WORKER_ACCESS_TOKEN}
```

`WORKER` 권한 회원만 호출할 수 있다.

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| inviteCode | string | Y | 6자리 숫자 초대 코드 |

#### 요청 본문

없음

#### 성공 응답

```http
201 Created
```

```json
{
  "crewId": 20,
  "workPlaceId": 10,
  "workPlaceName": "스위프",
  "joinStatus": "APPROVED",
  "crewRole": "WORKER",
  "status": "ACTIVE"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| crewId | number | 생성된 크루 ID |
| workPlaceId | number | 가입한 사업장 ID |
| workPlaceName | string | 가입한 사업장 이름 |
| joinStatus | string | 현재 `APPROVED` |
| crewRole | string | 현재 `WORKER` |
| status | string | 현재 `ACTIVE` |

#### 주요 비즈니스 규칙

- 근무자만 초대 코드를 수락할 수 있다.
- 초대 코드는 6자리 숫자 형식이어야 한다.
- 초대 코드는 한 번만 사용할 수 있다.
- 초대 코드가 만료되면 `EXPIRED` 상태로 기록한다.
- 초대 코드 실패 횟수가 5회 이상이면 `LOCKED` 상태로 기록한다.
- 같은 근무자가 같은 사업장에 중복 가입할 수 없다.
- 같은 초대 코드를 동시에 수락해도 하나의 요청만 성공한다.
- 수락 성공 시 `crew`가 `APPROVED / WORKER / ACTIVE` 상태로 생성된다.
- 수락 성공 시 초대 코드는 `USED` 상태가 된다.

#### Redis 실패 횟수 저장 규칙

```text
key: crew-invitation-attempt:{inviteCode}
value: failed attempt count
ttl: 1 hour
```

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4001 | 초대 코드 형식 오류, 존재하지 않는 코드, 만료된 코드, 사용 완료 코드, 잠긴 코드 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | WORKER 권한이 아님 |
| 409 | 4005 | 이미 해당 사업장 크루로 등록되어 있음 |

### 7.3 사업장 크루 초대 코드 이력 조회

사장님이 본인 사업장에서 생성한 비지명 초대 코드의 발급/사용 이력을 조회한다.

이 API는 “누구에게 초대 링크를 보냈는지”가 아니라 “어떤 초대 코드가 생성되었고, 사용되었다면 누가 수락했는지”를 반환한다.

```http
GET /api/work-places/{workPlaceId}/crew-invitations?page=0&size=20
```

#### 인증

```http
Authorization: Bearer {OWNER_ACCESS_TOKEN}
```

`OWNER` 권한 회원만 호출할 수 있다.

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 초대 코드 이력을 조회할 사업장 ID |

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| page | number | N | 0 | 0부터 시작하는 페이지 번호 |
| size | number | N | 20 | 페이지 크기. 1 이상 100 이하 |

#### 성공 응답

```http
200 OK
```

```json
{
  "content": [
    {
      "invitationId": 1,
      "workPlaceId": 10,
      "inviteCode": "839204",
      "inviteUrl": "chack-chack://crew-invitations/839204",
      "status": "USED",
      "expiresAt": "2026-06-13T15:00:00",
      "usedAt": "2026-06-13T14:10:00",
      "usedByMemberId": 20,
      "usedByMemberName": "정진섭",
      "failedAttemptCount": 0,
      "createdAt": "2026-06-13T14:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].invitationId | number | 크루 초대 ID |
| content[].workPlaceId | number | 사업장 ID |
| content[].inviteCode | string | 6자리 숫자 초대 코드 |
| content[].inviteUrl | string | 앱 딥링크 초대 URL |
| content[].status | string | 초대 코드 상태 |
| content[].expiresAt | string | 초대 코드 만료 시각 |
| content[].usedAt | string/null | 초대 코드 사용 시각 |
| content[].usedByMemberId | number/null | 초대 코드를 수락한 회원 ID 스냅샷 |
| content[].usedByMemberName | string/null | 초대 코드를 수락한 회원 이름. 회원 조회가 불가능하면 null |
| content[].failedAttemptCount | number | RDB에 기록된 실패 횟수 |
| content[].createdAt | string | 초대 코드 생성 시각 |
| page | number | 현재 페이지 번호 |
| size | number | 페이지 크기 |
| totalElements | number | 전체 초대 코드 수 |
| totalPages | number | 전체 페이지 수 |

#### 주요 비즈니스 규칙

- 사장님만 조회할 수 있다.
- 사장님 본인이 소유한 활성 사업장의 초대 코드만 조회할 수 있다.
- 초대 대상자를 지명하지 않는 구조이므로, 수락 전에는 `usedByMemberId`, `usedByMemberName`, `usedAt`이 null이다.
- 수락 완료된 초대 코드는 실제 수락한 근무자 정보가 `usedByMemberId`, `usedByMemberName`으로 표시된다.
- 기본 정렬은 최신 생성순이다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | `page`, `size` 검증 실패 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |
| 404 | 4004 | 조회할 수 있는 사업장을 찾을 수 없음 |

### 7.4 사업장 근무자 목록 조회

사장님 또는 근무자가 자신이 접근 가능한 사업장의 근무자 크루 목록을 조회한다.

```http
GET /api/work-places/{workPlaceId}/crews
```

#### 인증

```http
Authorization: Bearer {ACCESS_TOKEN}
```

`OWNER`, `WORKER` 모두 호출할 수 있다.

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 근무자 목록을 조회할 사업장 ID |

#### 사장님 성공 응답

사장님은 본인이 소유한 활성 사업장의 근무자 개인정보를 조회할 수 있다.

```http
200 OK
```

```json
{
  "crews": [
    {
      "crewId": 20,
      "memberId": 7,
      "name": "이세령",
      "phoneNumber": "01012345678",
      "profileImageUrl": "https://static.example.com/profile-images/7/profile.png",
      "crewRole": "WORKER",
      "joinStatus": "APPROVED",
      "crewStatus": "ACTIVE",
      "createdAt": "2026-07-01T10:00:00"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| crews[].crewId | number | 사업장 크루 ID |
| crews[].memberId | number | 근무자 회원 ID |
| crews[].name | string | 근무자 이름 |
| crews[].phoneNumber | string | 근무자 휴대폰 번호 |
| crews[].profileImageUrl | string/null | 근무자 프로필 이미지 URL. 없으면 `null` |
| crews[].crewRole | string | 현재 `WORKER` |
| crews[].joinStatus | string | 현재 응답에는 `APPROVED`만 포함 |
| crews[].crewStatus | string | 현재 응답에는 `ACTIVE`만 포함 |
| crews[].createdAt | string | 크루 등록 시각 |

#### 근무자 성공 응답

근무자는 같은 사업장 근무자의 이름과 프로필 이미지만 조회할 수 있다. 휴대폰 번호, 가입 상태, 크루 상태 등 개인정보성/관리성 필드는 내려주지 않는다.

```http
200 OK
```

```json
{
  "crews": [
    {
      "crewId": 20,
      "memberId": 7,
      "name": "이세령",
      "profileImageUrl": "https://static.example.com/profile-images/7/profile.png"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| crews[].crewId | number | 사업장 크루 ID |
| crews[].memberId | number | 근무자 회원 ID |
| crews[].name | string | 근무자 이름 |
| crews[].profileImageUrl | string/null | 근무자 프로필 이미지 URL. 없으면 `null` |

#### 주요 비즈니스 규칙

- 목록에는 `APPROVED / WORKER / ACTIVE` 상태이고 `deleted_at is null`인 크루만 포함한다.
- 사장님은 본인이 소유한 활성 사업장의 근무자 목록만 조회할 수 있다.
- 근무자는 본인이 `APPROVED / ACTIVE` 상태로 소속된 활성 사업장의 근무자 목록만 조회할 수 있다.
- OWNER 크루는 근무자 목록에 포함하지 않는다.
- 백엔드는 `crew`와 `member`를 fetch join으로 조회하고, 활성 프로필 이미지는 `memberId IN (...)`으로 한 번에 조회해 N+1 쿼리를 방지한다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | 근무자가 해당 사업장에 소속되어 있지 않음 |
| 404 | 4004 | 사업장이 없거나 사장님이 소유한 사업장이 아님 |

### 7.5 사업장 근무자 삭제

사장님이 본인 사업장의 근무자 크루를 비활성 처리한다.

```http
DELETE /api/work-places/{workPlaceId}/crews/{crewId}
```

#### 인증

```http
Authorization: Bearer {OWNER_ACCESS_TOKEN}
```

`OWNER` 권한 회원만 호출할 수 있다.

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 근무자를 삭제할 사업장 ID |
| crewId | number | Y | 삭제할 근무자 크루 ID |

#### 성공 응답

```http
204 No Content
```

#### 주요 비즈니스 규칙

- 사장님만 근무자 크루를 삭제할 수 있다.
- 사장님 본인이 소유한 활성 사업장의 근무자만 삭제할 수 있다.
- 삭제 대상은 `WORKER` 크루만 가능하다. OWNER 크루는 이 API로 삭제할 수 없다.
- 물리 삭제하지 않고 `crew.status = INACTIVE`, `crew.deleted_at = 삭제 시각`으로 비활성 처리한다.
- 비활성 처리된 근무자는 사업장 근무자 목록과 내 사업장 목록에서 제외된다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4001 | OWNER 크루 삭제 시도 등 잘못된 요청 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |
| 404 | 4004 | 사업장 또는 활성 근무자 크루를 찾을 수 없음 |

## 8. 공지사항 API

공지사항은 사업장 단위 게시글이다. 사장님은 본인 소유 사업장의 공지를 작성, 수정, 삭제할 수 있고 근무자는 본인이 소속된 사업장의 공지만 조회할 수 있다.

### 8.1 공지 이미지 업로드 URL 발급

공지 작성 화면에서 첨부할 이미지를 S3에 직접 업로드하기 위한 presigned PUT URL을 발급한다.
공지 이미지는 프로필 이미지와 같은 S3 버킷을 사용할 수 있지만, object key prefix는 `notice-images`로 분리한다.

```http
POST /api/work-places/{workPlaceId}/notice-images/upload-url
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 공지 이미지를 첨부할 사업장 ID |

#### 요청 본문

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| originalFileName | string | Y | 원본 파일명. 경로 문자와 제어 문자 불가, 최대 255자 |
| contentType | string | Y | 이미지 MIME type. `image/jpeg`, `image/png`, `image/webp` 허용 |
| fileSize | number | Y | 파일 크기 byte. 1 byte 이상 10MB 이하 |

#### 요청 예시

```json
{
  "originalFileName": "notice-image.png",
  "contentType": "image/png",
  "fileSize": 1024
}
```

#### 성공 응답

```http
200 OK
```

```json
{
  "uploadUrl": "https://s3-presigned-upload-url",
  "objectKey": "notice-images/1/1/7f5d8b4b.png",
  "storedFileName": "7f5d8b4b.png",
  "headers": {
    "Content-Type": "image/png"
  },
  "expiresInSeconds": 300
}
```

#### 클라이언트 처리 흐름

1. 공지 작성 화면에서 이미지 파일을 선택한다.
2. 이미지 1장마다 이 API를 호출해 `uploadUrl`, `objectKey`, `headers`를 받는다.
3. `uploadUrl`로 S3 PUT 업로드를 수행한다.
4. S3 PUT 성공 후 공지 작성 API의 `imageObjectKeys` 배열에 `objectKey`를 담아 전송한다.

#### S3 PUT 요청 주의사항

- `uploadUrl`은 백엔드 API URL이 아니라 AWS S3 URL이다.
- S3 PUT 요청에는 백엔드 JWT `Authorization` 헤더를 넣지 않는다.
- 응답의 `headers`를 S3 PUT 요청에 그대로 포함한다.
- S3 PUT body에는 이미지 파일 byte를 그대로 넣는다.
- S3 PUT 성공 응답은 보통 `200 OK`이고 body가 비어 있을 수 있다.

#### 주요 비즈니스 규칙

- 사장님만 발급할 수 있다.
- 사장님 본인이 소유한 활성 사업장에 대해서만 발급할 수 있다.
- 공지 이미지는 최대 10MB까지 허용한다.
- 공지 1개에는 최대 5장까지 첨부할 수 있다.
- 허용 파일 형식은 JPG, PNG, WEBP다.
- 서버는 업로드 URL 발급 시 1차로 파일명, content type, 파일 크기를 검증한다.
- 서버는 공지 작성 시 S3 객체의 실제 content type, 파일 크기, 매직 바이트를 다시 검증한다.
- 발급된 object key는 `notice-images/{workPlaceId}/{ownerMemberId}/{storedFileName}` 형식이다.

#### 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 파일명, content type, 파일 크기 검증 실패 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아니거나 본인 사업장이 아님 |
| 404 | 4004 | 조회 가능한 사업장을 찾을 수 없음 |

### 8.2 사업장 공지 작성

```http
POST /api/work-places/{workPlaceId}/notices
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 공지를 작성할 사업장 ID |

#### 요청 본문

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| title | string | Y | 공지 제목. 1~100자 |
| content | string | Y | 공지 내용. 1~5000자 |
| representative | boolean | Y | 대표 공지 여부 |

#### 요청 예시

```json
{
  "title": "오늘 마감 쓰레기 버리는 거 잊지마세요",
  "content": "쓰레기 안버려서 자꾸 오픈이 버립니다.",
  "representative": true
}
```

#### 성공 응답

```http
201 Created
```

```json
{
  "noticeId": 1,
  "workPlaceId": 1,
  "writerMemberId": 1,
  "writerMemberName": "정진섭",
  "title": "오늘 마감 쓰레기 버리는 거 잊지마세요",
  "content": "쓰레기 안버려서 자꾸 오픈이 버립니다.",
  "representative": true,
  "status": "ACTIVE",
  "images": [],
  "myReactionType": null,
  "reactions": [],
  "createdAt": "2026-06-14T03:00:00",
  "updatedAt": "2026-06-14T03:00:00"
}
```

#### 주요 비즈니스 규칙

- 사장님만 작성할 수 있다.
- 사장님 본인이 소유한 활성 사업장에만 작성할 수 있다.
- 대표 공지는 사업장당 최대 1개다.
- 새 공지를 대표 공지로 작성하면 같은 사업장의 기존 대표 공지는 자동 해제된다.

#### 8.2.1 공지 작성 시 이미지 첨부

공지 이미지를 첨부하려면 공지 작성 API를 호출하기 전에 `8.1 공지 이미지 업로드 URL 발급` API와 S3 PUT 업로드를 먼저 완료해야 한다.

공지 작성 요청 본문에는 선택 필드 `imageObjectKeys`를 추가할 수 있다.

```json
{
  "title": "개인 물품 보관 관련 안내",
  "content": "개인 물품은 지정된 보관함을 이용해주세요.",
  "representative": false,
  "imageObjectKeys": [
    "notice-images/1/1/7f5d8b4b.png",
    "notice-images/1/1/9a1c0e21.png"
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| imageObjectKeys | string[] | N | S3 PUT 업로드에 성공한 공지 이미지 object key 목록. 최대 5개 |

공지 작성 성공 응답의 `images` 배열에는 확정된 이미지 메타데이터가 포함된다.
이미지가 없는 공지는 빈 배열을 반환한다.

```json
{
  "noticeId": 1,
  "workPlaceId": 1,
  "writerMemberId": 1,
  "writerMemberName": "김단비",
  "title": "개인 물품 보관 관련 안내",
  "content": "개인 물품은 지정된 보관함을 이용해주세요.",
  "representative": false,
  "status": "ACTIVE",
  "images": [
    {
      "noticeImageId": 1,
      "originalFileName": "notice-image.png",
      "storedFileName": "7f5d8b4b.png",
      "objectKey": "notice-images/1/1/7f5d8b4b.png",
      "imageUrl": "https://static.example.com/notice-images/1/1/7f5d8b4b.png",
      "contentType": "image/png",
      "fileSize": 1024,
      "displayOrder": 1
    }
  ],
  "myReactionType": null,
  "reactions": [],
  "createdAt": "2026-06-28T14:00:00",
  "updatedAt": "2026-06-28T14:00:00"
}
```

#### 이미지 첨부 규칙

- 공지 1개당 이미지는 최대 5장이다.
- `imageObjectKeys` 순서가 공지 이미지 노출 순서가 된다.
- 같은 요청 안에서 동일한 `objectKey`를 중복 전달할 수 없다.
- `objectKey`는 현재 사장님이 해당 사업장에서 발급받은 PENDING 이미지여야 한다.
- 서버는 공지 작성 시 S3에 실제 업로드된 파일의 content type, 파일 크기, 매직 바이트를 검증한다.
- 검증에 성공한 이미지만 `ACTIVE` 상태가 되며 공지에 연결된다.
- 검증 실패 시 공지는 생성되지 않는다.

#### 8.2.2 공지 이미지 응답 공통 필드

공지 이미지가 포함되는 API는 `images` 배열을 반환한다. 이미지가 없는 공지는 빈 배열을 반환한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| images | array | 공지에 연결된 활성 이미지 목록 |
| images[].noticeImageId | number | 공지 이미지 ID |
| images[].originalFileName | string | 업로드 URL 발급 시 전달한 원본 파일명 |
| images[].storedFileName | string | S3 저장 파일명 |
| images[].objectKey | string | S3 object key |
| images[].imageUrl | string | 앱에서 바로 표시 가능한 public 이미지 URL |
| images[].contentType | string | S3 객체 content type |
| images[].fileSize | number | S3 객체 파일 크기 byte |
| images[].displayOrder | number | 공지 안에서 이미지 노출 순서. `1`부터 시작 |

`images` 배열이 포함되는 API는 다음과 같다.

```text
POST /api/work-places/{workPlaceId}/notices
GET /api/work-places/{workPlaceId}/notices
GET /api/work-places/{workPlaceId}/notices/representative
GET /api/notices/{noticeId}
PATCH /api/notices/{noticeId}
```

홈 화면 전용 요약 API는 현재 가벼운 공지 텍스트 노출 목적이므로 `images`를 반환하지 않는다.

```text
GET /api/home/work-places/{workPlaceId}/representative-notice
GET /api/home/work-places/{workPlaceId}/latest-notice
```

### 8.3 사업장 공지 목록 조회

```http
GET /api/work-places/{workPlaceId}/notices?page=0&size=20
Authorization: Bearer {accessToken}
```

#### 인증

```text
OWNER, WORKER
```

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| page | number | N | 0 | 페이지 번호. 0 이상 |
| size | number | N | 20 | 페이지 크기. 1~100 |

#### 성공 응답

```json
{
  "content": [
    {
      "noticeId": 1,
      "workPlaceId": 1,
      "writerMemberId": 1,
      "writerMemberName": "정진섭",
      "title": "오늘 마감 쓰레기 버리는 거 잊지마세요",
      "content": "쓰레기 안버려서 자꾸 오픈이 버립니다.",
      "representative": true,
      "status": "ACTIVE",
      "images": [
        {
          "noticeImageId": 1,
          "originalFileName": "notice-image.png",
          "storedFileName": "7f5d8b4b.png",
          "objectKey": "notice-images/1/1/7f5d8b4b.png",
          "imageUrl": "https://static.example.com/notice-images/1/1/7f5d8b4b.png",
          "contentType": "image/png",
          "fileSize": 1024,
          "displayOrder": 1
        }
      ],
      "myReactionType": "HEART",
      "reactions": [
        {
          "reactionType": "HEART",
          "count": 4
        },
        {
          "reactionType": "CHECK",
          "count": 6
        },
        {
          "reactionType": "NEUTRAL",
          "count": 2
        },
        {
          "reactionType": "SMILE",
          "count": 0
        },
        {
          "reactionType": "KISS",
          "count": 0
        },
        {
          "reactionType": "PROUD",
          "count": 1
        }
      ],
      "createdAt": "2026-06-14T03:00:00",
      "updatedAt": "2026-06-14T03:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

#### 주요 비즈니스 규칙

- 사장님은 본인 소유 사업장의 공지만 조회할 수 있다.
- 근무자는 승인된 활성 크루로 소속된 사업장의 공지만 조회할 수 있다.
- 삭제된 공지는 목록에 포함하지 않는다.
- 기본 정렬은 최신 작성순이다.
- `images`는 공지별 활성 이미지 목록이다. 이미지가 없는 공지는 빈 배열을 반환한다.
- 목록 조회는 공지 ID 목록으로 활성 이미지를 한 번에 조회하여 N+1 쿼리를 방지한다.
- `myReactionType`은 로그인한 회원이 선택한 공감이다. 선택한 공감이 없으면 `null`이다.
- `reactions`는 6개 공감 타입의 현재 활성 집계이며, 집계가 0인 타입도 포함한다.

### 8.4 사업장 대표 공지 조회

```http
GET /api/work-places/{workPlaceId}/notices/representative
Authorization: Bearer {accessToken}
```

#### 성공 응답: 대표 공지가 있는 경우

```json
{
  "notice": {
    "noticeId": 1,
    "workPlaceId": 1,
    "writerMemberId": 1,
    "writerMemberName": "정진섭",
    "title": "대표 공지",
    "content": "홈에서 노출할 대표 공지입니다.",
    "representative": true,
    "status": "ACTIVE",
    "images": [
      {
        "noticeImageId": 1,
        "originalFileName": "notice-image.png",
        "storedFileName": "7f5d8b4b.png",
        "objectKey": "notice-images/1/1/7f5d8b4b.png",
        "imageUrl": "https://static.example.com/notice-images/1/1/7f5d8b4b.png",
        "contentType": "image/png",
        "fileSize": 1024,
        "displayOrder": 1
      }
    ],
    "myReactionType": null,
    "reactions": [
      {
        "reactionType": "HEART",
        "count": 0
      },
      {
        "reactionType": "CHECK",
        "count": 0
      },
      {
        "reactionType": "NEUTRAL",
        "count": 0
      },
      {
        "reactionType": "SMILE",
        "count": 0
      },
      {
        "reactionType": "KISS",
        "count": 0
      },
      {
        "reactionType": "PROUD",
        "count": 0
      }
    ],
    "createdAt": "2026-06-14T03:00:00",
    "updatedAt": "2026-06-14T03:00:00"
  }
}
```

#### 성공 응답: 대표 공지가 없는 경우

```json
{
  "notice": null
}
```

### 8.5 공지 단건 조회

```http
GET /api/notices/{noticeId}
Authorization: Bearer {accessToken}
```

#### 주요 비즈니스 규칙

- 사장님은 본인 소유 사업장의 공지만 조회할 수 있다.
- 근무자는 승인된 활성 크루로 소속된 사업장의 공지만 조회할 수 있다.
- 삭제된 공지는 조회할 수 없다.

응답 형식은 `8.2 사업장 공지 작성` 성공 응답과 동일하다.
`images` 배열에는 공지에 연결된 활성 이미지 목록이 `displayOrder ASC` 순서로 포함된다.
이미지가 없는 공지는 `images: []`를 반환한다.

### 8.6 공지 공감 선택/변경/취소

근무자가 공지에 공감을 남긴다. 이미 같은 공감을 선택한 상태에서 다시 같은 공감을 요청하면 공감을 취소한다.

```http
PUT /api/notices/{noticeId}/reactions
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
WORKER
```

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| noticeId | number | Y | 공감을 선택할 공지 ID |

#### 요청 본문

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| reactionType | string | Y | 공감 종류. `HEART`, `CHECK`, `NEUTRAL`, `SMILE`, `KISS`, `PROUD` |

#### 요청 예시

```json
{
  "reactionType": "HEART"
}
```

#### 성공 응답

```json
{
  "noticeId": 1,
  "myReactionType": "HEART",
  "reactions": [
    {
      "reactionType": "HEART",
      "count": 4
    },
    {
      "reactionType": "CHECK",
      "count": 6
    },
    {
      "reactionType": "NEUTRAL",
      "count": 2
    },
    {
      "reactionType": "SMILE",
      "count": 0
    },
    {
      "reactionType": "KISS",
      "count": 0
    },
    {
      "reactionType": "PROUD",
      "count": 1
    }
  ]
}
```

#### 주요 비즈니스 규칙

- 근무자만 공감을 선택할 수 있다.
- 근무자는 승인된 활성 크루로 소속된 사업장의 공지에만 공감을 선택할 수 있다.
- 공감은 회원 1명이 공지 1개에 대해 최대 1개 타입만 선택할 수 있다.
- 여러 공감 타입을 동시에 선택할 수 없다. 서버는 `notice_id + member_id` 기준으로 활성 공감 0개 또는 1개만 유지한다.
- 기존 공감이 없는 상태에서 요청하면 새 공감을 생성한다.
- 기존 공감과 다른 공감을 요청하면 실패하지 않고 기존 row를 재사용하여 공감 종류를 변경한다.
- 기존 공감과 같은 공감을 요청하면 취소 처리되어 `myReactionType`은 `null`이 된다.
- 공감 회원 ID는 access token의 `memberId`를 사용하며 `member` 테이블과 FK를 걸지 않는 비정규화 컬럼으로 저장한다.

#### 공감 변경 예시

```text
공감 없음 + HEART 요청 -> HEART 활성
HEART 활성 + CHECK 요청 -> CHECK로 변경
CHECK 활성 + CHECK 요청 -> 공감 취소
공감 취소 상태 + PROUD 요청 -> PROUD 활성
```

### 8.7 공지 공감 취소

근무자가 현재 선택한 공감을 취소한다. 이미 선택한 공감이 없어도 성공 응답을 반환한다.

```http
DELETE /api/notices/{noticeId}/reactions
Authorization: Bearer {accessToken}
```

#### 인증

```text
WORKER
```

#### 성공 응답

```json
{
  "noticeId": 1,
  "myReactionType": null,
  "reactions": [
    {
      "reactionType": "HEART",
      "count": 3
    },
    {
      "reactionType": "CHECK",
      "count": 6
    },
    {
      "reactionType": "NEUTRAL",
      "count": 2
    },
    {
      "reactionType": "SMILE",
      "count": 0
    },
    {
      "reactionType": "KISS",
      "count": 0
    },
    {
      "reactionType": "PROUD",
      "count": 1
    }
  ]
}
```

#### 주요 비즈니스 규칙

- 근무자만 공감을 취소할 수 있다.
- 근무자는 승인된 활성 크루로 소속된 사업장의 공지에 대해서만 공감을 취소할 수 있다.
- 현재 활성 공감이 없으면 상태 변경 없이 현재 공감 집계만 반환한다.

### 8.8 홈 대표 공지 조회

홈 화면에서 대표 공지만 가볍게 노출하기 위한 전용 API다. 기존 대표 공지 조회 API보다 응답 필드가 작다.

```http
GET /api/home/work-places/{workPlaceId}/representative-notice
Authorization: Bearer {accessToken}
```

#### 인증

```text
OWNER, WORKER
```

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 홈 대표 공지를 조회할 사업장 ID |

#### 성공 응답: 대표 공지가 있는 경우

```json
{
  "notice": {
    "noticeId": 1,
    "title": "오늘 마감 쓰레기 버리는 거 잊지마세요",
    "content": "쓰레기 안버려서 자꾸 오픈이 버립니다.",
    "writerMemberName": "정진섭",
    "createdAt": "2026-06-14T03:00:00"
  }
}
```

#### 성공 응답: 대표 공지가 없는 경우

```json
{
  "notice": null
}
```

#### 주요 비즈니스 규칙

- 사장님은 본인 소유 사업장의 홈 대표 공지만 조회할 수 있다.
- 근무자는 승인된 활성 크루로 소속된 사업장의 홈 대표 공지만 조회할 수 있다.
- 대표 공지가 없으면 404가 아니라 `notice: null`을 반환한다.
- 홈 화면 전용 응답이므로 `status`, `representative`, `workPlaceId`는 반환하지 않는다.
- 홈 화면 전용 요약 응답이므로 `images`는 반환하지 않는다. 이미지가 필요한 화면은 공지 단건 조회 API를 추가로 호출한다.

### 8.9 홈 최신 공지 조회

홈 화면에서 가장 최근 작성된 공지 1건을 가볍게 노출하기 위한 전용 API다. 대표 공지 여부와 무관하게 활성 공지 중 최신 1건을 반환한다.

```http
GET /api/home/work-places/{workPlaceId}/latest-notice
Authorization: Bearer {accessToken}
```

#### 인증

```text
OWNER, WORKER
```

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | number | Y | 홈 최신 공지를 조회할 사업장 ID |

#### 성공 응답: 최신 공지가 있는 경우

```json
{
  "notice": {
    "noticeId": 10,
    "title": "최신 공지",
    "content": "가장 최근 작성된 공지입니다.",
    "writerMemberName": "정진섭",
    "createdAt": "2026-06-14T03:10:00"
  }
}
```

#### 성공 응답: 최신 공지가 없는 경우

```json
{
  "notice": null
}
```

#### 주요 비즈니스 규칙

- 사장님은 본인 소유 사업장의 홈 최신 공지만 조회할 수 있다.
- 근무자는 승인된 활성 크루로 소속된 사업장의 홈 최신 공지만 조회할 수 있다.
- 정렬 기준은 `createdAt DESC`, `noticeId DESC`다.
- 대표 공지가 가장 최근 작성된 공지라면 대표 공지 API와 최신 공지 API가 같은 공지를 반환할 수 있다.
- 최신 공지가 없으면 404가 아니라 `notice: null`을 반환한다.
- 홈 화면 전용 응답이므로 `status`, `representative`, `workPlaceId`는 반환하지 않는다.
- 홈 화면 전용 요약 응답이므로 `images`는 반환하지 않는다. 이미지가 필요한 화면은 공지 단건 조회 API를 추가로 호출한다.

### 8.10 공지 수정

```http
PATCH /api/notices/{noticeId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

요청 본문은 `title`, `content`, `representative`만 받는다.
공지 수정 API는 현재 이미지 추가, 교체, 삭제를 처리하지 않는다.
응답 형식은 `8.2 사업장 공지 작성` 성공 응답과 동일하며, 기존에 연결된 활성 이미지는 `images` 배열에 포함된다.

#### 주요 비즈니스 규칙

- 사장님만 수정할 수 있다.
- 사장님 본인이 소유한 사업장의 공지만 수정할 수 있다.
- 수정으로 대표 공지를 지정하면 같은 사업장의 기존 대표 공지는 자동 해제된다.

### 8.11 공지 삭제

```http
DELETE /api/notices/{noticeId}
Authorization: Bearer {accessToken}
```

#### 인증

```text
OWNER
```

#### 성공 응답

```http
204 No Content
```

#### 주요 비즈니스 규칙

- 사장님만 삭제할 수 있다.
- 사장님 본인이 소유한 사업장의 공지만 삭제할 수 있다.
- 삭제는 물리 삭제가 아니라 `DELETED` 상태와 `deleted_at`으로 처리한다.
- 대표 공지를 삭제하면 해당 사업장은 대표 공지가 없는 상태가 된다.

### 8.12 공지 댓글 작성

```http
POST /api/notices/{noticeId}/comments
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

#### 요청 본문

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| content | string | Y | 댓글 내용. 1~500자 |

#### 요청 예시

```json
{
  "content": "확인 부탁드립니다."
}
```

#### 성공 응답

```http
201 Created
```

```json
{
  "commentId": 1,
  "noticeId": 1,
  "writerMemberId": 1,
  "writerMemberName": "정진섭",
  "content": "확인 부탁드립니다.",
  "status": "ACTIVE",
  "createdAt": "2026-06-14T03:00:00",
  "updatedAt": "2026-06-14T03:00:00"
}
```

### 8.13 공지 댓글 조회

```http
GET /api/notices/{noticeId}/comments?cursorId=10&size=20
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| cursorId | number | N | null | 마지막으로 조회한 댓글 ID. 전달하면 해당 ID보다 큰 댓글부터 조회 |
| size | number | N | 20 | 조회 개수. 1~100 |

#### 성공 응답

```json
{
  "content": [
    {
      "commentId": 11,
      "noticeId": 1,
      "writerMemberId": 1,
      "writerMemberName": "정진섭",
      "content": "확인 부탁드립니다.",
      "status": "ACTIVE",
      "createdAt": "2026-06-14T03:00:00",
      "updatedAt": "2026-06-14T03:00:00"
    }
  ],
  "nextCursorId": 11,
  "hasNext": true
}
```

#### 주요 비즈니스 규칙

- 댓글은 `notice_comment_id` 오름차순으로 조회한다.
- 다음 페이지가 없으면 `nextCursorId`는 null이고 `hasNext`는 false다.
- 삭제된 댓글은 조회하지 않는다.

### 8.14 공지 댓글 수정

```http
PATCH /api/notices/{noticeId}/comments/{commentId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 인증

```text
OWNER
```

요청 본문과 응답 형식은 `8.11 공지 댓글 작성`과 동일하다.

### 8.15 공지 댓글 삭제

```http
DELETE /api/notices/{noticeId}/comments/{commentId}
Authorization: Bearer {accessToken}
```

#### 인증

```text
OWNER
```

#### 성공 응답

```http
204 No Content
```

#### 공지사항 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 요청 본문, 페이지, 커서 검증 실패 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 403 | 4003 | OWNER/WORKER 권한이 아니거나 사업장 공지 접근 권한 없음 |
| 404 | 4004 | 사업장, 공지, 댓글을 찾을 수 없음 |

## 9. 알림 API

알림은 회원 기준으로 저장된다. 앱 내 알림함은 FCM 발송 성공 여부와 무관하게 저장되며, `PUSH` 정책 알림만 활성 FCM 토큰별 발송을 시도한다.

### 9.1 FCM 토큰 등록 또는 갱신

```http
POST /api/fcm-tokens
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 본문

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| deviceId | string | Y | 앱 기기 식별자. 100자 이하 |
| token | string | Y | FCM registration token. 512자 이하 |
| platform | string | Y | `ANDROID`, `IOS` |
| appVersion | string | Y | 앱 버전. 30자 이하 |

#### 요청 예시

```json
{
  "deviceId": "device-uuid",
  "token": "fcm-registration-token",
  "platform": "ANDROID",
  "appVersion": "1.0.0"
}
```

#### 성공 응답

```json
{
  "fcmTokenId": 1,
  "deviceId": "device-uuid",
  "platform": "ANDROID",
  "appVersion": "1.0.0",
  "status": "ACTIVE",
  "lastRegisteredAt": "2026-06-14T19:00:00"
}
```

#### 주요 비즈니스 규칙

- 로그인한 회원의 토큰만 등록 또는 갱신한다.
- 같은 `member_id + device_id`가 이미 있으면 새 row를 만들지 않고 기존 row를 갱신한다.
- 다시 등록하면 `status`는 `ACTIVE`가 된다.
- FCM 토큰 row는 물리 삭제하지 않는다.

### 9.2 FCM 토큰 비활성화

```http
DELETE /api/fcm-tokens/devices/{deviceId}
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
204 No Content
```

#### 주요 비즈니스 규칙

- 로그인한 회원 본인의 `deviceId`에 해당하는 토큰만 비활성화한다.
- 토큰이 없어도 idempotent하게 `204 No Content`를 반환한다.
- 다른 회원의 같은 `deviceId` 토큰에는 영향을 주지 않는다.

### 9.2.1 FCM 푸시 수신 설정 조회/변경

앱 내부 기본 알림은 실제 기기 푸시가 아니라 알림함 조회 데이터이므로 항상 저장한다.
FCM 푸시는 실제 기기 알림을 발생시키므로 회원이 수신 여부를 직접 변경할 수 있다.

#### 조회

```http
GET /api/members/me/notification-settings
Authorization: Bearer {accessToken}
```

##### 성공 응답

```json
{
  "fcmPushEnabled": true
}
```

#### 변경

```http
PATCH /api/members/me/notification-settings
Authorization: Bearer {accessToken}
Content-Type: application/json
```

##### 요청 Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| fcmPushEnabled | boolean | Y | FCM 푸시 수신 여부 |

```json
{
  "fcmPushEnabled": false
}
```

##### 성공 응답

```json
{
  "fcmPushEnabled": false
}
```

#### 주요 비즈니스 규칙

- JWT 인증이 필요하다.
- OWNER, WORKER 모두 호출할 수 있다.
- 설정 row가 없으면 조회 시 기본값 `true`로 생성한다.
- `fcmPushEnabled=false`이면 `PushPolicy.PUSH` 알림도 앱 내부 알림만 저장하고 FCM delivery를 생성하지 않는다.
- `fcmPushEnabled=true`이고 활성 FCM 토큰이 있는 경우에만 FCM delivery 생성과 발송 이벤트 발행이 가능하다.

### 9.3 알림함 조회

```http
GET /api/notifications?cursorId=10&size=20
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| cursorId | number | N | null | 마지막으로 조회한 알림 ID. 전달하면 해당 ID보다 작은 알림부터 조회 |
| size | number | N | 20 | 조회 개수. 1~100 |

#### 성공 응답

```json
{
  "content": [
    {
      "notificationId": 10,
      "notificationType": "NOTICE",
      "pushPolicy": "PUSH",
      "title": "공지 알림",
      "body": "새 공지가 등록되었습니다.",
      "data": {
        "noticeId": "1"
      },
      "read": false,
      "readAt": null,
      "createdAt": "2026-06-14T19:00:00"
    }
  ],
  "nextCursorId": 10,
  "hasNext": true
}
```

#### 주요 비즈니스 규칙

- 본인 알림만 조회할 수 있다.
- 최신 알림부터 `notification_id DESC`로 조회한다.
- 다음 페이지가 없으면 `nextCursorId`는 null이고 `hasNext`는 false다.
- 삭제된 알림은 조회하지 않는다.

### 9.4 알림 단건 읽음 처리

```http
PATCH /api/notifications/{notificationId}/read
Authorization: Bearer {accessToken}
```

#### 성공 응답

```json
{
  "notificationId": 10,
  "notificationType": "NOTICE",
  "pushPolicy": "PUSH",
  "title": "공지 알림",
  "body": "새 공지가 등록되었습니다.",
  "data": {
    "noticeId": "1"
  },
  "read": true,
  "readAt": "2026-06-14T19:01:00",
  "createdAt": "2026-06-14T19:00:00"
}
```

#### 주요 비즈니스 규칙

- 본인 알림만 읽음 처리할 수 있다.
- 이미 읽은 알림은 기존 `readAt`을 유지한다.
- 다른 회원의 알림은 존재 여부를 노출하지 않고 `404`로 응답한다.

### 9.5 모든 알림 읽음 처리

```http
PATCH /api/notifications/read-all
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
204 No Content
```

#### 주요 비즈니스 규칙

- 본인의 미읽음 활성 알림만 읽음 처리한다.
- 다른 회원의 알림에는 영향을 주지 않는다.

### 9.6 FCM 테스트 푸시 발송

실제 비즈니스 이벤트와 무관하게 로그인한 회원 본인에게 FCM 앱 푸시를 발송해 로컬 수신 테스트를 진행한다.

```http
POST /api/notifications/test-push
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| title | string | Y | 테스트 푸시 제목. 100자 이하 |
| body | string | Y | 테스트 푸시 본문. 500자 이하 |
| data | object | N | FCM data payload. 최대 20개 |

```json
{
  "title": "FCM 테스트",
  "body": "테스트 푸시가 도착했어요.",
  "data": {
    "type": "FCM_TEST"
  }
}
```

#### 성공 응답

```json
{
  "notificationId": 123
}
```

#### 주요 비즈니스 규칙

- JWT 인증이 필요하다.
- OWNER, WORKER 모두 호출할 수 있다.
- 요청한 회원 본인에게만 발송한다.
- 다른 회원 ID를 요청으로 받지 않는다.
- `notification_type`은 `FCM_TEST`, `push_policy`는 `PUSH`로 저장한다.
- 기존 알림 발송 파이프라인과 동일하게 `notification`, `notification_delivery`를 생성하고 커밋 이후 FCM 발송을 시도한다.
- 활성 FCM 토큰이 없거나 회원의 FCM 푸시 수신 설정이 꺼져 있으면 앱 내 알림만 생성되고 FCM delivery는 생성되지 않는다.

### 9.7 내부 알림 발송 정책

도메인 기능은 공개 API가 아니라 내부 서비스 `NotificationCommandService.sendToMember(...)`를 호출해 알림을 생성한다.

- `IN_APP_ONLY`: `notification`만 저장하고 FCM 발송을 시도하지 않는다.
- `PUSH`: `notification` 저장 후 수신 회원의 FCM 푸시 수신 설정이 켜져 있고 활성 `fcm_token`이 있으면 FCM 발송을 시도한다.
- FCM 발송 시도 결과는 `notification_delivery`에 저장한다.
- Firebase 설정이 비활성화된 환경에서는 FCM 발송을 시도하지 않고 실패 delivery를 기록한다.
- Firebase가 등록되지 않은 토큰이라고 응답하면 해당 `fcm_token`을 `INACTIVE`로 변경한다.

### 9.8 알림 주요 에러

| HTTP Status | Code | 상황 |
| ---: | --- | --- |
| 400 | 4000 | 요청 본문, 페이지, 커서 검증 실패 |
| 401 | 4002 | access token 없음 또는 유효하지 않음 |
| 404 | 4004 | 알림을 찾을 수 없음 |

## 10. 크루 초대 DB 정책

### 10.1 crew_invitation

`crew_invitation`은 초대 코드 발급과 사용 이력을 남기는 감사 테이블이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| crew_invitation_id | BIGINT | N | PK |
| work_place_id | BIGINT | N | 사업장 ID, `work_place` FK |
| created_by_member_id | BIGINT | N | 초대 생성 회원 ID 스냅샷, FK 없음 |
| invite_code | CHAR(6) | N | 6자리 초대 코드 |
| status | VARCHAR(20) | N | 초대 상태 |
| expires_at | DATETIME | N | 만료 시각 |
| used_at | DATETIME | Y | 사용 시각 |
| used_by_member_id | BIGINT | Y | 초대 사용 회원 ID 스냅샷, FK 없음 |
| failed_attempt_count | INT | N | 실패 횟수 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 시각 |

#### 상태값

```text
ACTIVE
USED
EXPIRED
LOCKED
CANCELED
```

#### 제약조건

- `invite_code`는 UNIQUE다.
- `work_place_id`는 `work_place.work_place_id`를 참조한다.
- `created_by_member_id`는 FK를 걸지 않는 비정규화 컬럼이다.
- `used_by_member_id`는 FK를 걸지 않는 비정규화 컬럼이다.

#### 인덱스

```text
idx_crew_invitation_work_place_status (work_place_id, status)
idx_crew_invitation_invite_code_status (invite_code, status)
idx_crew_invitation_expires_at_status (expires_at, status)
```

### 10.2 crew 조회 인덱스

`crew`는 회원이 여러 사업장에 소속되는 구조를 표현한다. 홈 화면의 내 사업장 목록 조회는 로그인 회원의 승인된 활성 소속을 기준으로 사업장을 조회한다.

#### 주요 조회 조건

```text
member_id = ?
join_status = 'APPROVED'
status = 'ACTIVE'
deleted_at is null
```

#### 인덱스

```text
idx_crew_member_join_status_status_deleted_work_place (member_id, join_status, status, deleted_at, work_place_id)
idx_crew_work_place_role_join_status_status_deleted (work_place_id, crew_role, join_status, status, deleted_at, crew_id)
```

- `GET /api/work-places/me` 조회에서 사용한다.
- `GET /api/work-places/{workPlaceId}/crews` 조회에서 사용한다.
- `work_place`는 PK인 `work_place_id`로 조인한다.
- 백엔드는 `crew`와 `work_place`를 fetch join으로 조회하여 매장 목록 응답 생성 시 N+1 쿼리를 방지한다.
- 사업장 근무자 목록은 `crew`와 `member`를 fetch join하고 프로필 이미지를 `memberId IN (...)`으로 분리 조회해 N+1 쿼리를 방지한다.

## 11. 공지사항 DB 정책

### 11.1 notice

`notice`는 사업장별 공지 게시글을 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| notice_id | BIGINT | N | PK |
| work_place_id | BIGINT | N | 사업장 ID, `work_place` FK |
| writer_member_id | BIGINT | N | 공지 작성 회원 ID 스냅샷, FK 없음 |
| title | VARCHAR(100) | N | 공지 제목 |
| content | TEXT | N | 공지 내용 |
| representative | TINYINT(1) | N | 대표 공지 여부 |
| status | VARCHAR(20) | N | 공지 상태 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 시각 |

#### 상태값

```text
ACTIVE
DELETED
```

#### 제약조건

- `work_place_id`는 `work_place.work_place_id`를 참조한다.
- `writer_member_id`는 FK를 걸지 않는 비정규화 컬럼이다.
- `representative`는 `0`, `1`만 허용한다.
- 사업장당 대표 공지는 서비스 트랜잭션에서 최대 1개로 유지한다.

#### 인덱스

```text
idx_notice_work_place_status_deleted_created_id (work_place_id, status, deleted_at, created_at DESC, notice_id DESC)
idx_notice_work_place_representative_status_deleted (work_place_id, representative, status, deleted_at)
```

### 11.2 notice_image

`notice_image`는 공지 이미지 업로드 시도와 공지에 연결된 이미지 메타데이터를 저장한다.
실제 파일은 S3에 저장하며, DB에는 파일 조회와 운영에 필요한 메타데이터만 저장한다.
최종 관계는 `notice_image -> notice`이며, `work_place`와는 직접 FK 관계를 갖지 않는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| notice_image_id | BIGINT | N | PK |
| notice_id | BIGINT | Y | 공지 ID, `notice` FK. PENDING 단계에서는 NULL |
| uploader_member_id | BIGINT | N | 업로드 URL을 발급받은 사장님 회원 ID, FK 없음 |
| original_file_name | VARCHAR(255) | N | 원본 파일명 |
| stored_file_name | VARCHAR(100) | N | S3 저장 파일명 |
| object_key | VARCHAR(500) | N | S3 object key |
| image_url | VARCHAR(700) | N | 앱 표시용 public image URL |
| content_type | VARCHAR(50) | N | 이미지 content type |
| file_size | BIGINT | N | 파일 크기 byte |
| display_order | INT | N | 공지 안에서 이미지 노출 순서. PENDING은 0, ACTIVE는 1~5 |
| status | VARCHAR(20) | N | 이미지 상태 |
| uploaded_at | DATETIME | Y | S3 업로드 검증 후 확정 시각 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 시각 |

#### 상태값

```text
PENDING
ACTIVE
DELETED
```

#### 제약조건

- `notice_id`는 `notice.notice_id`를 참조한다.
- `uploader_member_id`는 FK를 걸지 않는 비정규화 컬럼이다.
- `object_key`는 전체 테이블에서 유일해야 한다.
- `display_order`는 0~5만 허용한다.

#### 인덱스

```text
uk_notice_image_object_key (object_key)
idx_notice_image_notice_status_deleted_order (notice_id, status, deleted_at, display_order, notice_image_id)
idx_notice_image_uploader_object_status (uploader_member_id, object_key, status, deleted_at)
```

### 11.3 notice_comment

`notice_comment`는 공지별 사장님 댓글을 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| notice_comment_id | BIGINT | N | PK |
| notice_id | BIGINT | N | 공지 ID, `notice` FK |
| writer_member_id | BIGINT | N | 댓글 작성 회원 ID 스냅샷, FK 없음 |
| content | VARCHAR(500) | N | 댓글 내용 |
| status | VARCHAR(20) | N | 댓글 상태 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 시각 |

#### 상태값

```text
ACTIVE
DELETED
```

#### 제약조건

- `notice_id`는 `notice.notice_id`를 참조한다.
- `writer_member_id`는 FK를 걸지 않는 비정규화 컬럼이다.

#### 인덱스

```text
idx_notice_comment_notice_status_deleted_id (notice_id, status, deleted_at, notice_comment_id)
```

### 11.4 notice_reaction

`notice_reaction`은 근무자가 공지에 선택한 공감을 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| notice_reaction_id | BIGINT | N | PK |
| notice_id | BIGINT | N | 공지 ID, `notice` FK |
| member_id | BIGINT | N | 공감을 선택한 회원 ID 스냅샷, FK 없음 |
| reaction_type | VARCHAR(20) | N | 공감 종류 |
| status | VARCHAR(20) | N | 공감 상태 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 취소 시각 |

#### 공감 종류

```text
HEART
CHECK
NEUTRAL
SMILE
KISS
PROUD
```

#### 상태값

```text
ACTIVE
DELETED
```

#### 제약조건

- `notice_id`는 `notice.notice_id`를 참조한다.
- `member_id`는 access token의 `memberId`를 저장하는 비정규화 컬럼이며 `member` FK를 걸지 않는다.
- `unique(notice_id, member_id)`로 회원 1명이 공지 1개에 대해 하나의 공감 row만 갖도록 보장한다.
- 따라서 한 회원이 같은 공지에 여러 공감 타입을 동시에 활성화할 수 없다.
- 같은 공감을 다시 누르거나 취소 API를 호출하면 row를 물리 삭제하지 않고 `DELETED` 상태와 `deleted_at`으로 처리한다.
- 취소 후 다시 공감하면 기존 row를 재사용하여 `ACTIVE` 상태로 복구한다.

#### 인덱스

```text
uk_notice_reaction_notice_member (notice_id, member_id)
idx_notice_reaction_notice_status_type (notice_id, status, reaction_type)
idx_notice_reaction_member_status (member_id, status)
```

## 12. 알림 DB 정책

### 12.1 fcm_token

`fcm_token`은 회원의 기기별 FCM registration token을 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| fcm_token_id | BIGINT | N | PK |
| member_id | BIGINT | N | 회원 ID, `member` FK |
| device_id | VARCHAR(100) | N | 앱 기기 식별자 |
| token | VARCHAR(512) | N | FCM registration token |
| platform | VARCHAR(20) | N | `ANDROID`, `IOS` |
| app_version | VARCHAR(30) | N | 앱 버전 |
| status | VARCHAR(20) | N | 토큰 상태 |
| last_registered_at | DATETIME | N | 마지막 등록 또는 갱신 시각 |
| last_used_at | DATETIME | Y | 마지막 발송 시도 시각 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 비활성화 시각 |

#### 상태값

```text
ACTIVE
INACTIVE
```

#### 제약조건

- `member_id`는 `member.member_id`를 참조한다.
- `member_id + device_id`는 UNIQUE다.
- row는 물리 삭제하지 않고 `INACTIVE`로 비활성화한다.

#### 인덱스

```text
idx_fcm_token_member_status (member_id, status)
idx_fcm_token_token (token)
```

### 12.2 member_notification_setting

`member_notification_setting`은 회원별 FCM 푸시 수신 설정을 저장한다. 앱 내부 기본 알림은 이 설정과 무관하게 항상 `notification`에 저장된다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| member_notification_setting_id | BIGINT | N | PK |
| member_id | BIGINT | N | 회원 ID, `member` FK |
| fcm_push_enabled | BIT(1) | N | FCM 푸시 수신 여부 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

#### 제약조건

- `member_id`는 `member.member_id`를 참조한다.
- `member_id`는 UNIQUE로 회원당 설정 1건만 허용한다.
- 설정 row가 없는 기존 회원은 애플리케이션에서 기본값 `true`로 취급한다.

#### 인덱스

```text
idx_member_notification_setting_member_id (member_id)
```

### 12.3 notification

`notification`은 회원별 앱 내 알림함 데이터를 저장한다. FCM 앱 푸시 대상 알림도 먼저 이 테이블에 저장된다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| notification_id | BIGINT | N | PK |
| receiver_member_id | BIGINT | N | 수신 회원 ID, `member` FK |
| notification_type | VARCHAR(50) | N | 알림 업무 유형 |
| push_policy | VARCHAR(20) | N | `IN_APP_ONLY`, `PUSH` |
| title | VARCHAR(100) | N | 알림 제목 |
| body | VARCHAR(500) | N | 알림 본문 |
| data | JSON | Y | 앱 라우팅 등에 사용할 부가 데이터 |
| read_at | DATETIME | Y | 읽음 처리 시각 |
| status | VARCHAR(20) | N | 알림 상태 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 시각 |

#### 상태값

```text
ACTIVE
DELETED
```

#### 제약조건

- `receiver_member_id`는 `member.member_id`를 참조한다.

#### 인덱스

```text
idx_notification_receiver_status_created (receiver_member_id, status, created_at)
idx_notification_receiver_read (receiver_member_id, read_at)
```

### 12.4 notification_delivery

`notification_delivery`는 FCM 앱 푸시 발송 시도와 결과 이력을 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| notification_delivery_id | BIGINT | N | PK |
| notification_id | BIGINT | N | 알림 ID, `notification` FK |
| fcm_token_id | BIGINT | Y | 발송 대상 FCM 토큰 ID 스냅샷, FK 없음 |
| channel | VARCHAR(20) | N | 발송 채널. 현재 `FCM` |
| status | VARCHAR(20) | N | 발송 결과 |
| provider_message_id | VARCHAR(255) | Y | Firebase provider message ID |
| error_code | VARCHAR(100) | Y | 발송 실패 코드 |
| error_message | VARCHAR(500) | Y | 발송 실패 메시지 |
| attempted_at | DATETIME | Y | 발송 시도 시각 |
| sent_at | DATETIME | Y | 발송 성공 시각 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 시각 |

#### 상태값

```text
SUCCESS
FAILED
```

#### 제약조건

- `notification_id`는 `notification.notification_id`를 참조한다.
- `fcm_token_id`는 FK를 걸지 않는 비정규화 컬럼이다.

#### 인덱스

```text
idx_notification_delivery_notification (notification_id)
idx_notification_delivery_status_attempted (status, attempted_at)
idx_notification_delivery_fcm_token (fcm_token_id)
```

### 12.5 FCM 운영 설정

Firebase Admin SDK는 Java 서버 SDK를 사용한다. 서버는 기본적으로 FCM 발송 비활성 상태로 기동한다.

| 환경변수 | 설명 |
| --- | --- |
| FCM_ENABLED | `true`이면 Firebase Admin SDK sender를 활성화한다. 기본값은 `false` |
| FCM_CREDENTIALS_PATH | Firebase 서비스 계정 JSON 파일 경로. 비어 있으면 Application Default Credentials를 사용한다 |

서비스 계정 JSON 파일 내용은 저장소와 API 명세에 기록하지 않는다.

## 13. 클라이언트 플로우

### 13.1 기존 회원 로그인

1. 앱에서 소셜 SDK 로그인 수행
2. Google은 `idToken`, Kakao는 `accessToken`, Apple은 `idToken + authorizationCode` 획득
3. `POST /api/auth/social-login` 호출
4. `LOGIN_SUCCESS`이면 토큰 저장 후 홈 진입

### 13.2 신규 회원가입

1. 앱에서 소셜 SDK 로그인 수행
2. `POST /api/auth/social-login` 호출
3. `SIGNUP_REQUIRED`이면 회원가입 화면으로 이동
4. 사용자가 공통 필수 정보, 역할, 약관, 역할별 필수 정보를 입력
5. 사장님은 `POST /api/auth/signup/owner` 호출
6. 근무자는 `POST /api/auth/signup/worker` 호출
7. `LOGIN_SUCCESS`이면 토큰 저장 후 홈 진입

주의: 회원가입 API 호출 시 소셜 인증 정보를 다시 전달해야 한다.

### 13.3 크루 초대

1. 사장님이 사업장 화면에서 초대 링크 생성을 요청한다.
2. 앱은 `POST /api/work-places/{workPlaceId}/crew-invitations`를 호출한다.
3. 서버는 6자리 초대 코드와 딥링크 URL을 반환한다.
4. 사장님은 초대 링크 또는 코드를 근무자에게 전달한다.
5. 근무자는 초대 코드 화면에서 코드를 입력한다.
6. 앱은 `POST /api/crew-invitations/{inviteCode}/accept`를 호출한다.
7. 성공하면 근무자는 해당 사업장 크루로 즉시 승인된다.

### 13.4 토큰 재발급

1. access token 만료 또는 인증 실패 감지
2. 저장된 refresh token과 deviceId로 `POST /api/auth/token/refresh` 호출
3. 성공 시 access token과 refresh token을 모두 교체 저장
4. 실패 시 로컬 토큰 삭제 후 로그인 화면으로 이동

### 13.5 로그아웃

1. 저장된 refresh token과 deviceId로 `POST /api/auth/logout` 호출
2. 성공 또는 실패와 관계없이 클라이언트 로컬 토큰 삭제 권장
3. 서버는 일치하는 Redis refresh token 세션만 삭제

### 13.6 회원탈퇴와 탈퇴 취소

1. 로그인한 사용자가 `DELETE /api/members/me` 호출
2. 서버는 회원 상태를 `WITHDRAWAL_PENDING`으로 변경하고 refresh token, FCM token을 정리
3. 이후 사용자가 30일 안에 다시 소셜 로그인하면 `LOGIN_SUCCESS`와 함께 `member.status = WITHDRAWAL_PENDING` 반환
4. 앱은 일반 홈 진입 대신 탈퇴 취소 안내 화면으로 이동
5. 사용자가 취소를 선택하면 `POST /api/members/me/withdrawal-cancel` 호출
6. 성공하면 회원 상태가 `ACTIVE`로 복구되고 `deleted_at`은 `null`이 됨

## 14. 현재 구현 참고사항

- 민감 정보와 배포 환경 정보는 API 명세에 기록하지 않는다.
- 배포 관련 설정은 별도 보안 채널에서 관리한다.

- 현재 회원가입 API는 요청한 `termsId`가 DB에 존재해야 한다.
- `phoneNumber`는 하이픈 없는 11자리 숫자 형식만 허용한다.
- 소셜 이메일은 provider 응답에 없을 수 있으며, 없어도 회원가입을 허용한다.
- refresh token은 RDB에 저장하지 않고 Redis에 hash로 저장한다.
- refresh token은 `deviceId`별로 독립적으로 유지한다.
- 회원탈퇴 신청 시 `member.deleted_at`은 탈퇴 신청 시각을 의미한다.
- 회원탈퇴 신청 후 30일 이후 개인정보 영구 삭제는 아직 미구현이며 추후 관리자 기능에서 물리 삭제로 처리한다.
- 사장님 회원가입은 최초 사업장과 사장님 crew 소속을 함께 생성한다.
- 근무자는 회원가입만으로 기본 기능 사용이 가능하며, 사업장 소속은 크루 초대 수락 흐름에서 처리한다.
- 크루 초대 코드는 Redis TTL을 사용하지만, 최종 상태와 감사 이력은 RDB `crew_invitation`에 기록한다.
- FCM 토큰은 기기별로 저장하며, 물리 삭제하지 않고 `INACTIVE`로 비활성화한다.
- 앱 내 알림은 FCM 발송 성공 여부와 무관하게 `notification`에 저장한다.
- FCM 발송 이력은 `notification_delivery`에 저장하며, `fcm_token_id`는 FK 없는 스냅샷 컬럼이다.

## 15. 프로필 API

프로필 API는 로그인한 회원 본인의 이름, 휴대폰 번호, 프로필 이미지를 조회/수정한다. 모든 백엔드 API는 JWT 인증이 필요하며 OWNER, WORKER 모두 사용할 수 있다.

프로필 이미지는 앱이 백엔드로 파일을 전송하지 않고, 백엔드가 발급한 S3 presigned PUT URL로 앱이 S3에 직접 업로드한다. 조회는 MVP 기준 public S3 object URL을 사용한다.

#### 공통 인증

```http
Authorization: Bearer {accessToken}
```

#### 프로필 이미지 공통 정책

| 항목 | 정책 |
| --- | --- |
| 보유 개수 | 회원 1명당 현재 프로필 이미지 0개 또는 1개 |
| 업로드 방식 | S3 presigned PUT URL 직접 업로드 |
| 조회 방식 | public S3 object URL |
| 허용 형식 | `image/jpeg`, `image/png`, `image/webp` |
| 허용 확장자 | `jpg`, `jpeg`, `png`, `webp` |
| 최대 크기 | 10MB |
| 이미지 검증 | 업로드 확정 시 S3 객체의 content type, 크기, 매직 바이트 검증 |
| 수정 방식 | 새 이미지 업로드 후 확정하면 기존 이미지 자동 교체 |
| 삭제 방식 | 현재 이미지 row를 `DELETED` 처리하고 S3 객체 삭제 요청 |

### 15.1 내 프로필 조회

```http
GET /api/members/me/profile
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
200 OK
```

```json
{
  "memberId": 1,
  "name": "정진섭",
  "phoneNumber": "01012345678",
  "profileImage": {
    "profileImageId": 10,
    "originalFileName": "profile.png",
    "storedFileName": "a1b2c3d4.png",
    "objectKey": "profile-images/1/a1b2c3d4.png",
    "imageUrl": "https://chackchack.shop/profile-images/1/a1b2c3d4.png",
    "contentType": "image/png",
    "fileSize": 1024,
    "uploadedAt": "2026-06-20T09:00:00"
  }
}
```

프로필 이미지가 없으면 `profileImage`는 `null`이다.

#### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| memberId | number | 회원 ID |
| name | string | 회원 이름 |
| phoneNumber | string | 하이픈 없는 11자리 휴대폰 번호 |
| profileImage | object/null | 현재 프로필 이미지. 없으면 `null` |
| profileImage.profileImageId | number | 프로필 이미지 ID |
| profileImage.originalFileName | string | 앱에서 업로드 URL 발급 시 전달한 원본 파일명 |
| profileImage.storedFileName | string | 서버가 생성한 저장 파일명 |
| profileImage.objectKey | string | S3 object key |
| profileImage.imageUrl | string | 앱에서 바로 표시 가능한 public 이미지 URL |
| profileImage.contentType | string | S3 객체 content type |
| profileImage.fileSize | number | S3 객체 파일 크기 byte |
| profileImage.uploadedAt | string | 업로드 확정 시각 |

### 15.2 내 프로필 수정

```http
PATCH /api/members/me/profile
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 Body

```json
{
  "name": "수정이름",
  "phoneNumber": "01099998888"
}
```

#### 검증 규칙

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| name | Y | 공백 불가, 최대 10자 |
| phoneNumber | Y | 하이픈 없이 11자리 숫자 |

#### 실패 예시

```json
{
  "name": "",
  "phoneNumber": "010-1234-5678"
}
```

위 요청은 `400 Bad Request`가 발생한다. 휴대폰 번호는 클라이언트에서 정규화한 뒤 하이픈 없이 11자리 숫자로 전달한다.

#### 성공 응답

```http
200 OK
```

응답 형식은 `15.1 내 프로필 조회`와 동일하다.

### 15.3 프로필 이미지 업로드 URL 발급

앱은 이미지 파일을 백엔드로 직접 전송하지 않는다. 먼저 백엔드에서 S3 presigned PUT URL을 발급받고, 앱이 해당 URL로 S3에 직접 업로드한다.

이 API를 호출하면 백엔드는 `profile_image`에 `PENDING` row를 생성한다. 기존에 확정되지 않은 `PENDING` row가 있으면 새 URL 발급 전에 기존 row를 `DELETED`로 전환한다.

```http
POST /api/members/me/profile-image/upload-url
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 Body

```json
{
  "originalFileName": "profile.png",
  "contentType": "image/png",
  "fileSize": 1024
}
```

#### 검증 규칙

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| originalFileName | Y | 확장자 포함 |
| contentType | Y | `image/jpeg`, `image/png`, `image/webp`만 허용 |
| fileSize | Y | 1 byte 이상, 10MB 이하 |

#### 추가 검증 규칙

- `originalFileName`에는 `/`, `\`, 제어 문자를 포함할 수 없다.
- 파일 확장자와 `contentType`이 일치해야 한다.
- `image/jpeg`는 `jpg`, `jpeg`만 허용한다.
- `image/png`는 `png`만 허용한다.
- `image/webp`는 `webp`만 허용한다.
- `fileSize`는 클라이언트가 선택한 파일의 byte 크기다.
- 최종 파일 크기는 업로드 확정 API에서 S3 객체 메타데이터로 다시 검증한다.

#### 성공 응답

```http
200 OK
```

```json
{
  "uploadUrl": "https://s3-presigned-upload-url",
  "objectKey": "profile-images/1/a1b2c3d4.png",
  "storedFileName": "a1b2c3d4.png",
  "headers": {
    "content-type": "image/png"
  },
  "expiresInSeconds": 300
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| uploadUrl | string | S3 PUT 업로드용 presigned URL |
| objectKey | string | 업로드 확정 API에 전달할 S3 object key |
| storedFileName | string | 서버가 생성한 저장 파일명 |
| headers | object | S3 PUT 요청에 그대로 포함해야 하는 헤더 |
| expiresInSeconds | number | presigned URL 만료 시간 |

#### Flutter 처리 순서

1. 앱에서 이미지 파일을 선택한다.
2. 파일명, MIME type, 파일 byte 크기를 구한다.
3. 이 API를 호출해 `uploadUrl`, `objectKey`, `headers`를 받는다.
4. `uploadUrl`로 S3 PUT 업로드를 수행한다.
5. S3 PUT 성공 후 `15.5 프로필 이미지 업로드 확정` API를 호출한다.

#### S3 PUT 요청 주의사항

`uploadUrl`은 백엔드 API URL이 아니라 AWS S3 URL이다. 이 요청에는 백엔드 JWT 인증을 넣지 않는다.

```http
PUT {uploadUrl}
Content-Type: image/png

{binary image file}
```

클라이언트는 응답의 `headers`를 S3 PUT 요청에 그대로 포함해야 한다.

반드시 지켜야 할 규칙:

- `Authorization: Bearer ...`를 넣지 않는다.
- `Content-Length`를 직접 넣지 않는다.
- `Host`를 직접 넣지 않는다.
- `Content-Type` 값은 비워두면 안 된다.
- `Content-Type`은 업로드 URL 발급 요청의 `contentType`과 같아야 한다.
- `multipart/form-data`로 보내지 않는다.
- JSON, form-data, raw text가 아니라 파일 binary 자체를 PUT body로 보낸다.
- S3 PUT 성공 응답은 일반적으로 `200 OK`이며 응답 body가 비어 있을 수 있다.

Postman으로 테스트할 때:

- Authorization 탭: `No Auth`
- Headers 탭: `Content-Type: image/png`
- Body 탭: `binary` 선택 후 실제 이미지 파일 선택

S3 PUT 실패 또는 사용자가 업로드를 취소한 경우, 앱은 다시 업로드 URL 발급 API를 호출하면 된다. 서버는 이전 `PENDING` row를 `DELETED` 처리하고 새 `PENDING` row를 생성한다.

### 15.4 S3 PUT 업로드

이 단계는 백엔드 API가 아니라 S3 직접 호출이다.

#### 요청

```http
PUT {uploadUrl}
Content-Type: {headers의 content-type 값}
```

Body:

```text
binary image file
```

#### 성공 응답

```http
200 OK
```

#### 흔한 실패 원인

| S3 오류 | 원인 | 해결 |
| --- | --- | --- |
| `SignatureDoesNotMatch` | `Content-Type`이 비어 있거나 발급 시 값과 다름 | 응답 `headers`의 content type을 그대로 넣기 |
| `SignatureDoesNotMatch` | `Authorization`, `Content-Length`, `Host` 등을 직접 추가 | 해당 헤더 제거 |
| `SignatureDoesNotMatch` | 이전에 발급받은 만료/구버전 URL 사용 | 업로드 URL 재발급 |
| `AccessDenied` | presigned URL 만료 또는 버킷 정책 문제 | URL 재발급 또는 S3 정책 확인 |

### 15.5 프로필 이미지 업로드 확정

S3 업로드 성공 후 앱은 백엔드에 확정 API를 호출한다. 백엔드는 S3 객체의 `HeadObject`와 앞부분 byte range를 조회해 실제 이미지 여부, content type, 크기 제한을 다시 검증한다.

```http
PUT /api/members/me/profile-image
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 Body

```json
{
  "objectKey": "profile-images/1/a1b2c3d4.png"
}
```

`objectKey`는 업로드 URL 발급 API 응답의 `objectKey`를 그대로 전달한다.

#### 성공 응답

```http
200 OK
```

응답 형식은 `15.1 내 프로필 조회`와 동일하다.

#### 주요 비즈니스 규칙

- 회원 1명은 현재 프로필 이미지 0개 또는 1개만 가진다.
- 업로드 URL 발급으로 생성된 `PENDING` row만 확정할 수 있다.
- 새 이미지를 확정하면 기존 `ACTIVE` row는 `DELETED`로 전환하고, 새 `PENDING` row를 `ACTIVE`로 전환한다.
- 기존 프로필 이미지가 없으면 등록으로 동작한다.
- 기존 프로필 이미지가 있으면 수정/교체로 동작한다.
- 확정되지 않은 이전 `PENDING` row가 있으면 새 업로드 URL 발급 시 `DELETED`로 전환한다.
- 기존 S3 객체와 실패한 업로드 객체 삭제는 DB 커밋 이후 best-effort로 요청한다.
- S3 객체 삭제 실패는 API 실패로 처리하지 않고 error log만 남긴다.
- `objectKey`는 본인 회원 ID 경로(`profile-images/{memberId}/...`)만 확정할 수 있다.
- 이미지 검증 실패 시 PENDING row를 `DELETED`로 전환하고 업로드된 S3 객체 삭제를 요청한 뒤 `400`을 반환한다.

#### 실패 케이스

| HTTP | 상황 |
| --- | --- |
| 400 | S3 객체가 이미지 파일이 아님 |
| 400 | S3 객체 content type이 허용되지 않음 |
| 400 | S3 객체 크기가 10MB 초과 |
| 400 | presigned URL 만료 시간이 지난 PENDING row 확정 |
| 403 | 본인 경로가 아닌 `objectKey` 확정 시도 |
| 404 | 확정 가능한 PENDING row가 없음 |
| 404 | S3에 해당 object가 없음 |

### 15.6 프로필 이미지 수정/교체

별도 수정 API는 없다. 현재 프로필 이미지가 있는 상태에서 `15.3 -> 15.4 -> 15.5` 흐름을 다시 수행하면 기존 이미지가 새 이미지로 교체된다.

#### 수정 플로우

1. 새 이미지로 업로드 URL 발급
2. 새 이미지를 S3 PUT 업로드
3. 업로드 확정 API 호출
4. 프로필 조회 API로 `profileImage.objectKey`, `profileImage.imageUrl` 변경 확인

### 15.7 프로필 이미지 삭제

```http
DELETE /api/members/me/profile-image
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
204 No Content
```

#### 주요 비즈니스 규칙

- 현재 프로필 이미지가 없으면 멱등하게 `204 No Content`를 반환한다.
- 현재 프로필 이미지가 있으면 DB row를 `DELETED` 상태로 전환하고 `deleted_at`을 기록한다.
- 실제 S3 객체 삭제는 DB 커밋 이후 best-effort로 요청한다.
- S3 객체 삭제 실패는 API 실패로 처리하지 않고 error log만 남긴다.

삭제 후 `GET /api/members/me/profile`을 호출하면 `profileImage`는 `null`이어야 한다.

### 15.8 Flutter 구현 예시

아래는 개념 예시다. 실제 프로젝트의 HTTP client, MIME type 추론, 파일 객체 API에 맞게 조정한다.

```dart
final uploadUrlResponse = await api.post(
  '/api/members/me/profile-image/upload-url',
  data: {
    'originalFileName': fileName,
    'contentType': contentType,
    'fileSize': fileSize,
  },
);

final uploadUrl = uploadUrlResponse.data['uploadUrl'];
final objectKey = uploadUrlResponse.data['objectKey'];
final headers = Map<String, String>.from(uploadUrlResponse.data['headers']);

await rawHttpClient.put(
  Uri.parse(uploadUrl),
  headers: headers,
  bodyBytes: imageBytes,
);

final profile = await api.put(
  '/api/members/me/profile-image',
  data: {
    'objectKey': objectKey,
  },
);
```

주의:

- S3 PUT에는 백엔드 API client의 기본 `Authorization` 헤더가 들어가면 안 된다.
- S3 PUT은 백엔드 base URL을 사용하지 않는다.
- S3 PUT은 파일 byte를 그대로 body에 넣는다.

### 15.9 profile_image DB 정책

`profile_image`는 회원별 프로필 이미지 업로드 시도와 현재 프로필 이미지 메타데이터를 저장한다. 실제 이미지 파일은 S3에 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| profile_image_id | BIGINT | N | PK |
| member_id | BIGINT | N | 회원 ID, `member` FK |
| original_file_name | VARCHAR(255) | N | 앱에서 전달한 원본 파일명 |
| stored_file_name | VARCHAR(100) | N | 서버가 생성한 저장 파일명 |
| object_key | VARCHAR(500) | N | S3 object key |
| image_url | VARCHAR(700) | N | 앱 표시용 이미지 URL |
| content_type | VARCHAR(50) | N | 이미지 content type |
| file_size | BIGINT | N | 파일 크기 byte |
| status | VARCHAR(20) | N | `PENDING`, `ACTIVE`, `DELETED` |
| uploaded_at | DATETIME | Y | 업로드 확정 시각. `PENDING` 상태에서는 `NULL` |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |
| deleted_at | DATETIME | Y | 삭제 처리 시각 |
| active_member_id | BIGINT | Y | `ACTIVE` 1건 보장용 generated column |
| pending_member_id | BIGINT | Y | `PENDING` 1건 보장용 generated column |

#### 제약조건과 인덱스

```text
fk_profile_image_member (member_id -> member.member_id)
uk_profile_image_object_key (object_key)
uk_profile_image_active_member (active_member_id)
uk_profile_image_pending_member (pending_member_id)
idx_profile_image_member_status_deleted (member_id, status, deleted_at)
```

`active_member_id`, `pending_member_id`는 MySQL generated column이다. `status = 'ACTIVE' and deleted_at is null`인 row만 `active_member_id = member_id`를 가지므로 회원별 현재 프로필 이미지 1건을 DB에서 보장한다. `PENDING`도 동일하게 회원별 업로드 대기 row 1건만 허용한다. `DELETED` 이력은 두 generated column이 `NULL`이므로 여러 건 저장할 수 있다.

### 15.10 프로필 이미지 S3 운영 설정

| 환경변수 | 설명 |
| --- | --- |
| AWS_REGION | S3 버킷 리전 |
| AWS_S3_PROFILE_IMAGE_BUCKET | 프로필 이미지 S3 버킷명 |
| AWS_S3_PROFILE_IMAGE_PUBLIC_BASE_URL | 앱 표시용 이미지 base URL. 추후 CloudFront 도메인 권장 |
| AWS_S3_PROFILE_IMAGE_OBJECT_KEY_PREFIX | S3 object key prefix. 기본 `profile-images` |
| AWS_S3_PROFILE_IMAGE_UPLOAD_URL_EXPIRES_SECONDS | presigned upload URL 만료 시간. 기본 300초 |
| AWS_S3_PROFILE_IMAGE_MAX_SIZE_BYTES | 최대 파일 크기. 기본 10485760 byte |

#### 공지 이미지 S3 운영 설정

| 환경변수 | 설명 |
| --- | --- |
| AWS_S3_NOTICE_IMAGE_BUCKET | 공지 이미지 S3 버킷명. 프로필 이미지와 같은 버킷 사용 가능 |
| AWS_S3_NOTICE_IMAGE_PUBLIC_BASE_URL | 공지 이미지 앱 표시용 public base URL |
| AWS_S3_NOTICE_IMAGE_OBJECT_KEY_PREFIX | 공지 이미지 S3 object key prefix. 기본 `notice-images` |
| AWS_S3_NOTICE_IMAGE_UPLOAD_URL_EXPIRES_SECONDS | 공지 이미지 presigned upload URL 만료 시간. 기본 300초 |
| AWS_S3_NOTICE_IMAGE_MAX_SIZE_BYTES | 공지 이미지 최대 파일 크기. 기본 10485760 byte |
| NOTICE_IMAGE_MAX_COUNT | 공지 1개당 최대 이미지 첨부 개수. 기본 5 |

#### S3 버킷 정책 요약

- 업로드는 백엔드가 발급한 presigned PUT URL로만 수행한다.
- 조회는 MVP 기준 `profile-images/*`, `notice-images/*` public read를 허용한다.
- 버킷 전체 public open은 사용하지 않는다.
- ACL은 사용하지 않는다.
- 삭제/검증/URL 발급 권한은 백엔드 IAM principal만 가진다.

## 16. 스케줄 조건 생성 API

### 16-1. 스케줄 조건 생성 API

### API 개요

사장이 특정 사업장의 주간 스케줄 조건을 생성한다.

스케줄 조건 생성 시 `week_schedule`, `day`, `time_detail` 테이블에 값이 저장된다.

---

### Request

### Method

```
POST
```

### URL

```
/api/work-places/{workPlaceId}/schedule-conditions
```

### Header

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceId | Long | Y | 스케줄 조건을 생성할 사업장 ID |

---

### HTTP Body

```json
{
  "workPlaceOpenTime": "09:00:00",
  "workPlaceCloseTime": "22:00:00",
  "minPersonalWorkCount": 1,
  "maxPersonalWorkCount": 4,
  "dueDate": "2026-06-21",
  "days": [
    {
      "dayName": "MONDAY",
      "date": "2026-06-22",
      "groupingId": 1,
      "workChangeCount": 1,
      "holidayStatus": false,
      "selectLimitStatus": false,
      "timeDetails": [
        {
          "workPartNo": 1,
          "timeName": "오픈",
          "workerCount": 2,
          "startTime": "09:00:00",
          "closeTime": "13:00:00",
          "restTime": 0
        },
        {
          "workPartNo": 2,
          "timeName": "마감",
          "workerCount": 2,
          "startTime": "13:00:00",
          "closeTime": "22:00:00",
          "restTime": 30
        }
      ]
    },
    {
      "dayName": "TUESDAY",
      "date": "2026-06-23",
      "groupingId": 1,
      "workChangeCount": 1,
      "holidayStatus": false,
      "selectLimitStatus": false,
      "timeDetails": [
        {
          "workPartNo": 1,
          "timeName": "오픈",
          "workerCount": 2,
          "startTime": "09:00:00",
          "closeTime": "13:00:00",
          "restTime": 0
        },
        {
          "workPartNo": 2,
          "timeName": "마감",
          "workerCount": 2,
          "startTime": "13:00:00",
          "closeTime": "22:00:00",
          "restTime": 30
        }
      ]
    },
    {
      "dayName": "WEDNESDAY",
      "date": "2026-06-24",
      "groupingId": 1,
      "workChangeCount": 1,
      "holidayStatus": false,
      "selectLimitStatus": false,
      "timeDetails": [
        {
          "workPartNo": 1,
          "timeName": "오픈",
          "workerCount": 2,
          "startTime": "09:00:00",
          "closeTime": "13:00:00",
          "restTime": 0
        },
        {
          "workPartNo": 2,
          "timeName": "마감",
          "workerCount": 2,
          "startTime": "13:00:00",
          "closeTime": "22:00:00",
          "restTime": 30
        }
      ]
    },
    {
      "dayName": "THURSDAY",
      "date": "2026-06-25",
      "groupingId": 1,
      "workChangeCount": 1,
      "holidayStatus": false,
      "selectLimitStatus": false,
      "timeDetails": [
        {
          "workPartNo": 1,
          "timeName": "오픈",
          "workerCount": 2,
          "startTime": "09:00:00",
          "closeTime": "13:00:00",
          "restTime": 0
        },
        {
          "workPartNo": 2,
          "timeName": "마감",
          "workerCount": 2,
          "startTime": "13:00:00",
          "closeTime": "22:00:00",
          "restTime": 30
        }
      ]
    },
    {
      "dayName": "FRIDAY",
      "date": "2026-06-26",
      "groupingId": 2,
      "workChangeCount": 0,
      "holidayStatus": false,
      "selectLimitStatus": false,
      "timeDetails": [
        {
          "workPartNo": 1,
          "timeName": "전체",
          "workerCount": 1,
          "startTime": "09:00:00",
          "closeTime": "22:00:00",
          "restTime": 30
        }
      ]
    },
    {
      "dayName": "SATURDAY",
      "date": "2026-06-27",
      "groupingId": 2,
      "workChangeCount": 0,
      "holidayStatus": false,
      "selectLimitStatus": false,
      "timeDetails": [
        {
          "workPartNo": 1,
          "timeName": "전체",
          "workerCount": 1,
          "startTime": "09:00:00",
          "closeTime": "22:00:00",
          "restTime": 30
        }
      ]
    },
    {
      "dayName": "SUNDAY",
      "date": "2026-06-28",
      "groupingId": null,
      "workChangeCount": 0,
      "holidayStatus": true,
      "selectLimitStatus": false,
      "timeDetails": []
    }
  ]
}
```

---

### HTTP Body 필드 설명

#### workPlaceOpenTime

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceOpenTime | String | Y | 사업장 오픈 시간 |

#### workPlaceCloseTime

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPlaceCloseTime | String | Y | 사업장 마감 시간 |

#### minPersonalWorkCount

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| minPersonalWorkCount | Integer | Y | 인원당 최소 근무수 |

#### maxPersonalWorkCount

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| maxPersonalWorkCount | Integer | Y | 인원당 최대 근무수 |

#### dueDate

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| dueDate | String(LocalDate) | Y | 근무 불가능 시간 제출 마감일. 예: `2026-06-21` |

### days

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| days | Array | Y | 요일별 스케줄 조건 목록, 요일을 선택을 하던 안하던 무조건 7일의 요일 값들을 넣어야 합니다. |

### days 내부 필드

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| dayName | String | Y | 요일 이름 |
| date | String | Y | 해당 요일의 일자 |
| groupingId | Integer | N | UI에서 함께 선택된 요일 그룹 ID, 요일 선택이 안됐을시엔 null로 저장 |
| workChangeCount | Integer | Y | 근무 교대 횟수, 기본값은 0 |
| holidayStatus | Boolean | Y | 휴무일 여부 |
| selectLimitStatus | Boolean | Y | 근무자 선택 제한 여부 |
| timeDetails | Array | Y | 해당 요일의 근무 타임 상세 목록 |

---

### dayName 허용값

```
MONDAY
TUESDAY
WEDNESDAY
THURSDAY
FRIDAY
SATURDAY
SUNDAY
```

---

### groupingId 설명

UI에서 같은 조건으로 묶인 요일들은 같은 `groupingId`를 가진다.

예시:

```
월, 화, 수, 목, 토 → groupingId = 1
금, 일 → groupingId = 2
```

저장 예시:

```
MONDAY    groupingId = 1
TUESDAY   groupingId = 1
WEDNESDAY groupingId = 1
THURSDAY  groupingId = 1
SATURDAY  groupingId = 1

FRIDAY    groupingId = 2
SUNDAY    groupingId = 2
```

---

### workChangeCount 규칙

`workChangeCount`는 근무 시간이 몇 번 교대되는지를 의미한다.

```
workChangeCount = 0 (모든 요일에 대한 기본값)
→ timeDetails 1개

workChangeCount = 1
→ timeDetails 2개

workChangeCount = 2
→ timeDetails 3개
```

검증 규칙:

```
timeDetails.size() == workChangeCount + 1
```

---

### holiday_status & select_limit_status 규칙

holiday_status와 select_limit_status는 휴일상태와 근무자들 선택 제한 상태를 의미한다.

```
holiday_status
- true(1) : 휴일
- false(0) : 일반 영업 상태

select_limit_status
- true(1) : 선택불가
- false(0) : 선택가능
```

---

### timeDetails 내부 필드

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| workPartNo | Integer | Y | 근무 파트 번호 |
| timeName | String | N | 근무 타임 이름 |
| workerCount | Integer | Y | 해당 타임에 필요한 근무자 수 |
| startTime | String | Y | 근무 시작 시간 |
| closeTime | String | Y | 근무 종료 시간 |
| restTime | Integer | Y | 휴게 시간. 분 단위 |

---

### workPartNo 설명

회원별 어떤 근무 타임에 있는지 식별하기 위한 번호이다.

---

### Response

### 성공

```
HTTP/1.1 201 Created
```

```json
{
  "weekScheduleId": 1,
  "workPlaceId": 10,
  "weekScheduleName": "6월 3주차",
  "dueDate": "2026-06-21",
  "status": "ACTIVE",
  "createdAt": "2026-06-20T09:00:00",
  "updatedAt": "2026-06-20T09:00:00"
}
```

---

#### Response 필드 설명

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| weekScheduleId | Long | 생성된 주간 스케줄 조건 ID |
| workPlaceId | Long | 사업장 ID |
| weekScheduleName | String | 서버가 생성한 주차 이름 |
| dueDate | LocalDate | 근무자 제출 마감일 |
| status | String | 주간 스케줄 조건 상태 |
| createdAt | LocalDateTime | 생성 시간 |
| updatedAt | LocalDateTime | 수정 시간 |

---

### 서버 자동 생성 값

클라이언트가 보내지 않고 서버에서 생성하는 값은 다음과 같다.

| 값 | 생성 방식 |
| --- | --- |
| weekScheduleName | 현재 날짜 기준 `"월 주차"` 형식으로 생성 |
| status | `ACTIVE` |

---

### 주요 에러

| HTTP Status | Code | 상황 |
| --- | --- | --- |
| 400 | 4000 | days가 비어 있음 |
| 400 | 4000 | groupingId가 1 미만 |
| 400 | 4000 | workChangeCount가 0 미만 |
| 400 | 4000 | timeDetails 개수가 workChangeCount + 1과 일치하지 않음 |
| 400 | 4000 | 사업장 오픈 시간이 마감 시간보다 늦거나 같음 |
| 400 | 4000 | 근무 시작 시간이 종료 시간보다 늦거나 같음 |
| 400 | 4000 | 근무 시간이 사업장 운영 시간 범위를 벗어남 |
| 400 | 4000 | 최소 근무 횟수가 최대 근무 횟수보다 큼 |
| 400 | 4000 | 동일 요일 내 workPartNo가 중복됨 |
| 401 | 4002 | Access Token이 없거나 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |
| 404 | 4004 | 스케줄 조건을 생성할 수 있는 사업장을 찾을 수 없음 |

---

### Error Response 예시

### 요청값 검증 실패

```
HTTP/1.1 400 Bad Request
```

```json
{
  "code": "4000",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    {
      "field": "days",
      "message": "요일별 스케줄 조건은 최소 1개 이상 필요합니다."
    }
  ]
}
```

### 권한 없음

```
HTTP/1.1 403 Forbidden
```

```json
{
  "code": "4003",
  "message": "접근 권한이 없습니다."
}
```

### 사업장 없음

```
HTTP/1.1 404 Not Found
```

```json
{
  "code": "4004",
  "message": "조회할 수 있는 사업장을 찾을 수 없습니다."
}
```

### 16-2. 달력 활성화용 API

### API 정보

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate` |
| 설명 | 가장 최근에 생성된 스케줄 조건의 일자별 선택 가능 상태를 조회한다. |
| 권한 | WORKER |

---

### Path Variable

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| workPlaceId | Long | 사업장 ID |

---

### Request Header

| 이름 | 값 |
| --- | --- |
| Authorization | Bearer {accessToken} |

---

### Response Body

```json
{
  "weekScheduleId": 1,
  "workPlaceId": 10,
  "weekScheduleName": "6월 2주차",
  "dueDate": "2026-06-13",
  "availableDates": [
    {
      "date": "2026-06-07",
      "dayName": "SUNDAY",
      "holidayStatus": true,
      "selectLimitStatus": false
    },
    {
      "date": "2026-06-08",
      "dayName": "MONDAY",
      "holidayStatus": false,
      "selectLimitStatus": false
    },
    {
      "date": "2026-06-09",
      "dayName": "TUESDAY",
      "holidayStatus": false,
      "selectLimitStatus": false
    },
    {
      "date": "2026-06-10",
      "dayName": "WEDNESDAY",
      "holidayStatus": false,
      "selectLimitStatus": false
    },
    {
      "date": "2026-06-11",
      "dayName": "THURSDAY",
      "holidayStatus": false,
      "selectLimitStatus": false
    },
    {
      "date": "2026-06-12",
      "dayName": "FRIDAY",
      "holidayStatus": false,
      "selectLimitStatus": true
    },
    {
      "date": "2026-06-13",
      "dayName": "SATURDAY",
      "holidayStatus": false,
      "selectLimitStatus": false
    }
  ]
}
```

---

#### Response 필드 설명

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| weekScheduleId | Long | 주간 스케줄 ID |
| workPlaceId | Long | 사업장 ID |
| weekScheduleName | String | 주간 스케줄 이름 |
| dueDate | LocalDate | 불가능 시간 제출 마감일 |
| availableDates | Array | 일자별 선택 가능 상태 목록 |
| availableDates[].date | LocalDate | 해당 일자 |
| availableDates[].dayName | String | 요일명 |
| availableDates[].holidayStatus | Boolean | 휴일 여부 (`true`: 휴일, `false`: 일반 영업일) |
| availableDates[].selectLimitStatus | Boolean | 선택 제한 여부 (`true`: 선택 불가, `false`: 선택 가능) |

---

### 달력 활성화 규칙

| holidayStatus | selectLimitStatus | 상태 |
| --- | --- | --- |
| false | false | 선택 가능 |
| true | false | 선택 불가 (휴일) |
| false | true | 선택 불가 (선택 제한) |
| true | true | 선택 불가 |

---

### 예외 응답

| HTTP Status | Code | 상황 |
| --- | --- | --- |
| 401 | 4002 | Access Token이 없거나 유효하지 않음 |
| 403 | 4003 | 해당 사업장에 접근 권한이 없음 |
| 404 | 4004 | 진행 중인 스케줄 조건을 찾을 수 없음 |
| 500 | 500 | 서버 내부 오류 발생 |

### 16-3. 특정 일자 타임 상세 조회 API

### API 정보

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/days/{date}/time-details` |
| 설명 | 특정 일자의 근무 타임 상세 정보를 조회한다. |
| 권한 | WORKER |

---

### Path Variable

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| weekScheduleId | Long | 주간 스케줄 ID |
| date | LocalDate | 조회할 일자 (yyyy-MM-dd 형식) |

---

### Request Header

| 이름 | 값 |
| --- | --- |
| Authorization | Bearer {accessToken} |

---

### Response Body

```json
{
  "weekScheduleId": 1,
  "date": "2026-06-11",
  "dayName": "THURSDAY",
  "timeDetails": [
    {
      "timeDetailId": 10,
      "timeName": "오픈",
      "startTime": "09:00",
      "closeTime": "13:00",
      "workerCount": 1
    },
    {
      "timeDetailId": 11,
      "timeName": "미들",
      "startTime": "13:00",
      "closeTime": "18:00",
      "workerCount": 1
    },
    {
      "timeDetailId": 12,
      "timeName": "마감",
      "startTime": "18:00",
      "closeTime": "22:00",
      "workerCount": 2
    }
  ]
}
```

---

#### Response 필드 설명

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| weekScheduleId | Long | 주간 스케줄 ID |
| date | LocalDate | 조회한 일자 |
| dayName | String | 조회한 일자의 요일명 |
| timeDetails | Array | 해당 일자의 근무 타임 목록 |
| timeDetails[].timeDetailId | Long | 타임 상세 ID |
| timeDetails[].timeName | String | 타임 이름 |
| timeDetails[].startTime | LocalTime | 근무 시작 시간 |
| timeDetails[].closeTime | LocalTime | 근무 종료 시간 |
| timeDetails[].workerCount | Long | 해당 타임에 필요한 근무자 수 |

---

### 처리 규칙

| 조건 | 설명 |
| --- | --- |
| weekScheduleId가 존재해야 함 | 활성 상태의 주간 스케줄만 조회 가능 |
| date가 해당 주간 스케줄에 포함되어야 함 | 존재하지 않는 날짜는 조회 불가 |
| 타임 정보는 workPartNo 오름차순으로 조회 | 오픈 → 미들 → 마감 순서 보장(파트 나눠서 지은 제목에 따라 달라질 수 있음) |
| 삭제되지 않은 타임 정보만 조회 | status = ACTIVE, deletedAt IS NULL |

---

### 예외 응답

| HTTP Status | Code | 상황 |
| --- | --- | --- |
| 401 | 4002 | Access Token이 없거나 유효하지 않음 |
| 403 | 4003 | 해당 사업장 또는 스케줄에 접근 권한이 없음 |
| 404 | 4004 | 주간 스케줄을 찾을 수 없음 |
| 404 | 4004 | 해당 일자의 스케줄 조건을 찾을 수 없음 |
| 500 | 500 | 서버 내부 오류 발생 |

### 16-4. 최근 스케줄 조건 불러오기 API

### API 정보

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/schedule-conditions/latest` |
| 설명 | 사장이 가장 최근에 생성한 스케줄 조건 1건을 조회한다. |
| 권한 | OWNER |

---

### Path Variable

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| workPlaceId | Long | 사업장 ID |

---

### Request Header

| 이름 | 값 |
| --- | --- |
| Authorization | Bearer {accessToken} |

---

### Response Body

```json
{
  "weekScheduleId": 1,
  "workPlaceId": 10,
  "weekScheduleName": "2026년 6월 3주차",
  "nextWeekScheduleName": "2026년 6월 5주차",
  "dueDate": "2026-06-21",
  "groups": [
    {
      "groupingId": 1,
      "dayNames": [
        "MONDAY",
        "TUESDAY",
        "WEDNESDAY",
        "THURSDAY",
        "FRIDAY"
      ],
      "workPlaceOpenTime": "09:00:00",
      "workPlaceCloseTime": "21:00:00",
      "minPersonalWorkCount": 1,
      "maxPersonalWorkCount": 3,
      "timeDetails": [
        {
          "timeDetailId": 1,
          "workPartNo": 1,
          "timeName": "오전근무",
          "workerCount": 1,
          "startTime": "09:00:00",
          "closeTime": "15:00:00",
          "restMinutes": 0
        },
        {
          "timeDetailId": 2,
          "workPartNo": 2,
          "timeName": "오후근무",
          "workerCount": 2,
          "startTime": "15:00:00",
          "closeTime": "21:00:00",
          "restMinutes": 0
        }
      ]
    }
  ]
}
```

---

#### Response 필드 설명

| 필드명 | 타입 | 설명                               |
| --- | --- |----------------------------------|
| weekScheduleId | Long | 주간 스케줄 ID                        |
| workPlaceId | Long | 사업장 ID                           |
| weekScheduleName | String | 조회된 주간 스케줄 이름                    |
| nextWeekScheduleName | String | 생성할 주간 스케줄 이름                    |
| dueDate | LocalDate | 근무자 불가능 시간 제출 마감일                 |
| groups | Array | groupingId 기준으로 묶인 요일별 스케줄 조건 목록 |
| groups[].groupingId | Integer | 요일 그룹 ID                         |
| groups[].dayNames | Array | 같은 그룹에 속한 요일 목록                  |
| groups[].workPlaceOpenTime | LocalTime | 해당 그룹의 사업장 오픈 시간                 |
| groups[].workPlaceCloseTime | LocalTime | 해당 그룹의 사업장 마감 시간                 |
| groups[].minPersonalWorkCount | Long | 근무자 1명당 최소 근무 횟수                 |
| groups[].maxPersonalWorkCount | Long | 근무자 1명당 최대 근무 횟수                 |
| groups[].timeDetails | Array | 해당 그룹의 타임별 상세 정보                 |
| groups[].timeDetails[].timeDetailId | Long | 타임 상세 ID                         |
| groups[].timeDetails[].workPartNo | Long | 근무 파트 번호                         |
| groups[].timeDetails[].timeName | String | 근무 타임 이름                         |
| groups[].timeDetails[].workerCount | Long | 해당 타임에 필요한 근무자 수                 |
| groups[].timeDetails[].startTime | LocalTime | 근무 시작 시간                         |
| groups[].timeDetails[].closeTime | LocalTime | 근무 종료 시간                         |
| groups[].timeDetails[].restMinutes | Integer | 휴게 시간. 분 단위                      |

---

### 처리 규칙

| 조건 | 설명 |
| --- | --- |
| 가장 최근 스케줄 조건 1건 조회 | `createdAt` 내림차순, `weekScheduleId` 내림차순 기준 |
| 활성 상태만 조회 | `status = ACTIVE`, `deletedAt IS NULL` |
| 사장만 조회 가능 | `@OwnerOnly` 적용 |
| 본인 소유 사업장만 조회 가능 | 로그인한 사장의 사업장인지 검증 |
| 요일 조건은 groupingId 기준으로 묶어서 반환 | 같은 groupingId를 가진 요일들은 하나의 group으로 응답 |
| dayNames는 date 오름차순으로 정렬 | 일자 순서대로 요일 반환 |
| timeDetails는 workPartNo 오름차순으로 정렬 | 오픈 → 미들 → 마감 순서 보장 |

---

### 예외 응답

| HTTP Status | Code | 상황 |
| --- | --- | --- |
| 401 | 4002 | Access Token이 없거나 유효하지 않음 |
| 403 | 4003 | OWNER 권한이 아님 |
| 404 | 4004 | 조회할 수 있는 사업장을 찾을 수 없음 |
| 404 | 4004 | 최근 스케줄 조건을 찾을 수 없음 |
| 500 | 500 | 서버 내부 오류 발생 |

## 17. 근무 불가 시간 선택 API

### 17-1. 근무 불가 시간 선택 API

### API 개요

근무자가 자신이 근무할 수 없는 날짜 및 타임을 선택하여 제출한다.

제출 현황은 `worker_select_submission` 테이블에 저장되고, 선택한 타임 정보는 `worker_unavailable_time_detail` 테이블에 저장된다.

선택한 값이 없는 경우에는 `worker_select_submission`에만 저장되고 `worker_unavailable_time_detail`에는 저장되지 않는다.

제출이 완료되면 재제출은 불가하다.

---

### API 정보

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/work-places/{workPlaceId}/worker-select` |
| 권한 | WORKER |
| 설명 | 근무자가 근무 불가능한 날짜 및 타임을 제출한다. |

---

### Path Variable

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| workPlaceId | Long | 사업장 ID |

---

### Header

```
Authorization: Bearer {accessToken}
```

---

### HTTP Body

```json
{
  "weekScheduleId": 1,
  "timeDetails": [
    1,
    2,
    3
  ]
}
```

---

### HTTP Body 필드 설명

| 필드명 | 타입   | 필수 | 설명                           |
| --- |------| --- |------------------------------|
| weekScheduleId | Long | O | 근무 불가 시간을 제출할 주차 스케줄 ID |
| timeDetails | List<Long> | X | 선택한 근무 불가 타임 ID 목록 (`null`이면 빈 리스트로 처리) |

---

### 선택한 타임이 없는 경우

```json
{
  "weekScheduleId": 1,
  "timeDetails": []
}
```

---

### Response

### 성공 응답 (201 Created)

```json
{
  "workPlaceId": 1,
  "memberId": 3,
  "timeDetails": [
    {
      "timeDetailId": 1,
      "dayName": "MONDAY",
      "date": "2026-06-22",
      "workPartNo": 1,
      "timeName": "오픈"
    },
    {
      "timeDetailId": 2,
      "dayName": "MONDAY",
      "date": "2026-06-22",
      "workPartNo": 2,
      "timeName": "마감"
    },
    {
      "timeDetailId": 7,
      "dayName": "FRIDAY",
      "date": "2026-06-26",
      "workPartNo": 1,
      "timeName": "전체"
    }
  ]
}
```

---

#### Response 필드 설명

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| workPlaceId | Long | 사업장 ID |
| memberId | Long | 제출한 회원 ID |
| timeDetails | List | 제출한 근무 불가 타임 목록 |
| timeDetailId | Long | 타임 상세 ID |
| dayName | String | 요일명 |
| date | LocalDate | 날짜 |
| workPartNo | Long | 근무 파트 번호 |
| timeName | String | 근무 타임 이름 |

---

### 선택한 타임이 없는 경우 응답

→ 모든 타임 전부 근무 가능한 경우

```json
{
  "workPlaceId": 1,
  "memberId": 3,
  "timeDetails": []
}
```

---

### 주요 에러

| HTTP Status | 메시지 |
| --- | --- |
| 401 | 인증 정보가 올바르지 않습니다. |
| 403 | 이 회원은 해당 사업장의 크루원이 아닙니다. |
| 404 | 조회할 수 있는 근무 시간대 정보를 찾을 수 없습니다. |
| 400 | 해당 타임 정보는 해당 사업장에 속하지 않습니다. |

---

### Error Response 예시

```json
{
  "code": "4004",
  "message": "조회할 수 있는 근무 시간대 정보를 찾을 수 없습니다.",
  "errors": [],
  "path": "/api/work-places/1/worker-select",
  "timestamp": "2026-07-03T14:30:00"
}
```

---

### 17-2. 근무자 제출 여부 조회 API

### API 개요

사업장에 소속된 근무자들의 근무 불가 시간 제출 여부를 조회한다.

조회 기준은 다음과 같다.

- 해당 사업장의 활성 크루 목록 조회
- `crew_role`이 `WORKER`인 멤버만 조회
- 크루의 memberId가 worker_select_submission 테이블에 존재하면 제출 완료
- 존재하지 않으면 미제출
- 이 API는 제출 여부만 제공하며, 근무자가 선택한 상세 `time_detail` 목록은 응답하지 않는다.

---

### API 정보

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status` |
| 권한 | OWNER |
| 설명 | 사업장 근무자들의 제출 여부를 조회한다. |

---

### Path Variable

| 필드명 | 타입 | 설명        |
| --- | --- |-----------|
| workPlaceId | Long | 사업장 ID    |
| weekScheduleId | Long | 주차 스케줄 ID |

---

### Header

```
Authorization: Bearer {accessToken}
```

---

### Response

### 성공 응답 (200 OK)

```json
{
  "workPlaceId": 1,
  "weekScheduleId": 2,
  "workers": [
    {
      "memberId": 2,
      "memberName": "김철수",
      "submitted": true
    },
    {
      "memberId": 3,
      "memberName": "이영희",
      "submitted": false
    },
    {
      "memberId": 4,
      "memberName": "박민수",
      "submitted": true
    }
  ]
}
```

---

#### Response 필드 설명

| 필드명 | 타입 | 설명         |
| --- | --- |------------|
| workPlaceId | Long | 사업장 ID     |
| weekScheduleId | Long | 주차 스케줄 ID  |
| workers | List | 사업장 근무자 목록 |
| memberId | Long | 회원 ID      |
| memberName | String | 회원 이름      |
| submitted | Boolean | 제출 여부      |

---

### 제출 여부 판단 기준

| 값 | 설명 |
| --- | --- |
| true | worker_select_submission 테이블에 ACTIVE 상태 데이터 존재 → 제출함 |
| false | worker_select_submission 테이블에 데이터 없음 → 제출 안함 |

---

### 주요 에러

| HTTP Status | 메시지 |
|-------------| --- |
| 401         | 인증 정보가 올바르지 않습니다. |
| 403         | 권한이 없습니다. |
| 404         | 사업장을 찾을 수 없습니다. |
| 404         | 해당 주차의 스케줄 조건을 찾을 수 없습니다.|

---

### Error Response 예시

```json
{
  "code": "4003",
  "message": "권한이 없습니다.",
  "errors": [],
  "path": "/api/work-places/1/week-schedules/2/worker-select/status",
  "timestamp": "2026-07-03T14:30:00"
}
```

---

## 18. 자동 스케줄 생성/미리보기/확정 API

### 18-1. 자동 스케줄 생성 API

사장이 특정 주간 스케줄 조건(`week_schedule`)과 근무자의 불가 시간 제출(`worker_select_submission`, `worker_unavailable_time_detail`)을 기준으로 가능한 스케줄 후보를 생성한다.

생성 결과는 `schedule_generation_run`에 실행 이력으로 저장되고, 가능한 후보 N개는 `schedule_preview.preview_data` JSON 안에 한 번에 저장된다.

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs` |
| 권한 | OWNER |

#### 성공 응답: 201 Created

```json
{
  "scheduleGenerationRunId": 1,
  "schedulePreviewId": 1,
  "workPlaceId": 1,
  "weekScheduleId": 10,
  "candidateCount": 100,
  "status": "GENERATED"
}
```

#### 정책

- 사장만 호출할 수 있다.
- 요청자는 해당 사업장의 소유자여야 한다.
- `weekScheduleId`는 해당 사업장에 속한 활성 스케줄 조건이어야 한다.
- 자동 스케줄 생성은 현재 날짜 기준 다음 주 월요일부터 일요일까지 7일로 구성된 스케줄 조건에 대해서만 가능하다.
- 자동 스케줄 생성 시점까지 근무 불가 조건 제출을 완료한 활성 근무자만 생성 대상에 포함한다.
- 제출하지 않은 근무자는 자동 스케줄 생성 대상에서 제외한다.
- 제출 완료 근무자가 0명이면 생성할 수 없다.
- `schedule_preview`는 후보 1건당 row를 만들지 않는다.
- 가능한 모든 후보는 `preview_data.candidates` 배열 안에 저장한다.
- `previewNo`, `score`는 DB 컬럼이 아니라 JSON 내부 필드다.
- 같은 `weekScheduleId`에 활성 `GENERATED` 자동 생성 결과가 이미 있으면 일반 생성 API는 `409 Conflict`를 반환한다.
- 이미 생성된 미리보기를 다시 만들려면 `18-2. 자동 스케줄 재생성 API`를 사용한다.

#### 자동 생성 실패 응답 메시지

자동 생성 조건을 만족하지 못하면 `409 Conflict`, `code=4005`가 반환됩니다.
클라이언트는 `message`를 사용자 안내 문구로 활용할 수 있습니다.

| message | 클라이언트 안내 방향 |
| --- | --- |
| 자동 스케줄을 생성할 제출 완료 근무자가 없습니다. | 근무자 제출 현황을 확인하도록 안내 |
| 근무자별 최소 근무 횟수가 최대 근무 횟수보다 큽니다. | 스케줄 조건의 최소/최대 근무 횟수 수정 안내 |
| 전체 필요 근무 횟수가 근무자별 최소 근무 횟수 합계보다 적습니다. | 필요 근무 인원 또는 최소 근무 횟수 조정 안내 |
| 전체 근무 슬롯 수가 근무자별 최소 근무 횟수 합계보다 적습니다. | 필요 근무 인원 또는 최소 근무 횟수 조정 안내 |
| 전체 필요 근무 횟수가 근무자별 최대 근무 횟수 합계를 초과합니다. | 필요 근무 인원 감소 또는 최대 근무 횟수 증가 안내 |
| 근무 불가 조건 때문에 `{날짜} {타임명}({시작}-{종료})` 시간대에 필요한 인원을 배정할 수 없습니다. | 해당 시간대 근무 불가 제출/필요 인원 확인 안내 |
| 근무자 `{memberId}`번은 근무 불가 조건 때문에 최소 근무 횟수를 채울 수 없습니다. | 해당 근무자의 근무 불가 제출 또는 최소 근무 횟수 확인 안내 |
| 근무 불가 조건을 반영하면 근무자별 최대 근무 횟수 안에서 모든 슬롯을 채울 수 없습니다. | 근무 불가 제출, 필요 인원, 최대 근무 횟수 확인 안내 |
| 스케줄 조합이 너무 많아 자동 생성 탐색 한도를 초과했습니다. 근무 시간대나 필요 인원을 줄여주세요. | 조건을 단순화하도록 안내 |
| 근무자별 최소/최대 근무 횟수와 근무 불가 조건을 동시에 만족하는 조합이 없습니다. | 전체 조건 재검토 안내 |

---

### 18-2. 자동 스케줄 재생성 API

기존 활성 자동 스케줄 생성 결과와 미리보기 스냅샷을 삭제 처리한 뒤, 같은 주간 스케줄 조건으로 새 자동 스케줄 미리보기를 생성합니다.

재생성도 일반 생성과 동일하게 현재 날짜 기준 다음 주 월요일부터 일요일까지 7일로 구성된 스케줄 조건에 대해서만 가능합니다.

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/regenerate` |
| 권한 | OWNER |

#### 성공 응답: 201 Created

```json
{
  "scheduleGenerationRunId": 2,
  "schedulePreviewId": 2,
  "workPlaceId": 1,
  "weekScheduleId": 10,
  "candidateCount": 50,
  "status": "GENERATED"
}
```

#### 정책

- 사장만 호출할 수 있다.
- 요청자는 해당 사업장의 소유자여야 한다.
- 기존 활성 `GENERATED` run은 `DELETED` 상태로 변경되고 `deletedAt`이 기록된다.
- 기존 활성 `schedule_preview`도 `DELETED` 상태로 변경되고 `deletedAt`이 기록된다.
- 새 생성 과정이 실패하면 같은 트랜잭션 안에서 롤백되어 기존 생성 결과 삭제 처리도 반영되지 않는다.
- 재생성 결과는 새 `schedule_generation_run`과 새 `schedule_preview`로 저장된다.

---

### 18-3. 자동 스케줄 미리보기 조회 API

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/{runId}/preview` |
| 권한 | OWNER |

#### 성공 응답: 200 OK

```json
{
  "scheduleGenerationRunId": 1,
  "schedulePreviewId": 1,
  "workPlaceId": 1,
  "weekScheduleId": 10,
  "candidateCount": 2,
  "previewData": {
    "candidateCount": 2,
    "candidates": [
      {
        "candidateNo": 1,
        "score": 95,
        "days": [
          {
            "dayId": 100,
            "timeDetails": [
              {
                "timeDetailId": 1000,
                "workerMemberIds": [3, 4]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

---

### 18-4. 주간 스케줄 확정 API

사장이 미리보기 JSON 안의 후보 중 하나를 선택하여 실제 확정 스케줄로 전환한다.

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules` |
| 권한 | OWNER |

#### 요청 Body

```json
{
  "scheduleGenerationRunId": 1,
  "schedulePreviewId": 1,
  "selectedCandidateNo": 1
}
```

#### 성공 응답: 201 Created

```json
{
  "confirmedWeekScheduleId": 1,
  "workPlaceId": 1,
  "weekScheduleId": 10,
  "selectedCandidateNo": 1,
  "assignmentCount": 14,
  "status": "ACTIVE"
}
```

#### 정책

- 하나의 활성 `week_schedule`에는 활성 확정 스케줄을 1개만 만들 수 있다.
- 확정 시 `confirmed_week_schedule` row 1개와 `confirmed_schedule_assignment` row N개를 생성한다.
- `confirmed_schedule_assignment`는 `time_detail` 기준으로 근무자를 배정한다.
- `work_date`, `start_time`, `close_time`, `rest_time`은 확정 배정 테이블에 중복 저장하지 않는다.
- `selectedCandidateNo`가 미리보기 JSON에 없으면 확정할 수 없다.
- 미리보기 JSON 안의 `timeDetailId`는 해당 `weekScheduleId`에 실제로 속해야 한다.

#### 주요 에러

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | 4001 | 선택한 후보 번호가 미리보기 JSON에 없음 |
| 401 | 4002 | 인증 정보 없음 또는 올바르지 않음 |
| 403 | 4003 | OWNER가 아니거나 해당 사업장 소유자가 아님 |
| 404 | 4004 | 사업장, 주간 스케줄, 생성 이력, 미리보기를 찾을 수 없음 |
| 409 | 4005 | 근무자 제출 미완료, 후보 없음, 이미 확정된 스케줄 존재 |

---

### 18-5. 확정 스케줄 단건 근무 파트 추가 API

사장이 이미 확정된 주간 스케줄 안에서 단건 근무 파트(`time_detail`)와 근무자 배정(`confirmed_schedule_assignment`)을 직접 추가한다.

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/assignments` |
| 권한 | OWNER |

#### 요청 Body

```json
{
  "workDate": "2026-07-06",
  "workPartNo": 3,
  "timeName": "야간",
  "startTime": "22:00",
  "closeTime": "23:00",
  "restTime": 0,
  "workerMemberIds": [3, 4]
}
```

#### 성공 응답: 201 Created

```json
{
  "confirmedWeekScheduleId": 1,
  "workPlaceId": 1,
  "weekScheduleId": 10,
  "dayId": 100,
  "timeDetailId": 1003,
  "workDate": "2026-07-06",
  "workPartNo": 3,
  "timeName": "야간",
  "startTime": "22:00:00",
  "closeTime": "23:00:00",
  "restTime": 0,
  "workerMemberIds": [3, 4],
  "assignmentCount": 2
}
```

#### 정책

- 확정된 주간 스케줄(`confirmed_week_schedule`)이 있어야만 추가할 수 있다.
- `workDate`는 확정 스케줄이 바라보는 `week_schedule` 하위의 활성 `day.date` 중 하나여야 한다.
- 아직 자동 스케줄 조건이 생성되지 않은 다다음주 이후 날짜 등은 추가할 수 없다.
- 같은 `day` 안에 동일한 `workPartNo`의 활성 `time_detail`이 이미 있으면 추가할 수 없다.
- `workerMemberIds`는 중복될 수 없다.
- 모든 `workerMemberIds`는 해당 사업장에 승인된 활성 근무자여야 한다.
- `startTime`은 `closeTime`보다 빨라야 한다.

---

### 18-6. 확정 스케줄 단건 근무 파트 수정 API

사장이 확정된 근무 파트를 수정한다. 수정은 기존 `time_detail`과 해당 활성 확정 배정을 `DELETED` 처리한 뒤, 요청 값으로 새 `time_detail`과 새 확정 배정을 생성하는 방식이다.

| 항목 | 내용 |
| --- | --- |
| Method | PUT |
| URL | `/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments` |
| 권한 | OWNER |

#### 요청 Body

```json
{
  "workDate": "2026-07-06",
  "workPartNo": 4,
  "timeName": "수정타임",
  "startTime": "10:00",
  "closeTime": "14:00",
  "restTime": 30,
  "workerMemberIds": [4]
}
```

#### 성공 응답: 200 OK

```json
{
  "confirmedWeekScheduleId": 1,
  "workPlaceId": 1,
  "weekScheduleId": 10,
  "dayId": 100,
  "timeDetailId": 1004,
  "workDate": "2026-07-06",
  "workPartNo": 4,
  "timeName": "수정타임",
  "startTime": "10:00:00",
  "closeTime": "14:00:00",
  "restTime": 30,
  "workerMemberIds": [4],
  "assignmentCount": 1
}
```

#### 정책

- `timeDetailId`는 해당 확정 스케줄의 `week_schedule` 하위 활성 `time_detail`이어야 한다.
- 요청 `workDate`는 해당 확정 스케줄의 `week_schedule` 하위 활성 `day.date` 중 하나여야 한다.
- 요청 날짜가 기존 슬롯 날짜와 달라도, 같은 확정 주간 스케줄 안의 날짜라면 수정할 수 있다.
- 기존 `time_detail`을 직접 변경하지 않고 `DELETED` 처리 후 새 `time_detail`을 생성한다.
- 기존 활성 `confirmed_schedule_assignment`도 `DELETED` 처리하고 새 배정을 생성한다.

---

### 18-7. 확정 스케줄 단건 근무 파트 삭제 API

사장이 확정된 근무 파트를 삭제한다. 해당 `time_detail`과 그 근무 파트에 묶인 활성 확정 배정을 `DELETED` 처리한다.

| 항목 | 내용 |
| --- | --- |
| Method | DELETE |
| URL | `/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments` |
| 권한 | OWNER |

#### 성공 응답: 200 OK

```json
{
  "confirmedWeekScheduleId": 1,
  "timeDetailId": 1000,
  "deletedAssignmentCount": 1,
  "status": "DELETED"
}
```

#### 주요 에러

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | 4001 | 요청 날짜가 확정 주간 스케줄 기간에 포함되지 않음, 시간 범위 오류, 근무자 중복 |
| 401 | 4002 | 인증 정보 없음 또는 올바르지 않음 |
| 403 | 4003 | OWNER가 아니거나 해당 사업장의 소유자가 아님 |
| 404 | 4004 | 사업장, 확정 주간 스케줄, 근무 파트를 찾을 수 없음 |
| 409 | 4005 | 같은 날짜에 동일한 근무 파트 번호가 이미 존재함 |

---

### 18-8. 근무자 본인 확정 스케줄 달력 조회 API

근무자가 본인에게 배정된 확정 근무 일정을 기간 기준으로 조회한다. 지난 확정 근무 일정, 이번 주 진행 중 일정, 다음 주 확정 일정 모두 같은 API로 조회한다.

이 API는 실제 출근/퇴근 기록이 아니라 `confirmed_schedule_assignment` 기준의 확정 근무 일정 조회 API다.

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/me/confirmed-schedules?from={from}&to={to}` |
| 권한 | WORKER |

#### Query Parameter

| 필드명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| from | LocalDate | O | 조회 시작일 |
| to | LocalDate | O | 조회 종료일 |

#### 성공 응답: 200 OK

```json
{
  "from": "2026-07-01",
  "to": "2026-07-31",
  "schedules": [
    {
      "workPlaceId": 1,
      "workPlaceName": "스위프",
      "workDate": "2026-07-06",
      "dayName": "MONDAY",
      "timeDetailId": 10,
      "timeName": "오픈",
      "workPartNo": 1,
      "startTime": "09:00:00",
      "closeTime": "13:00:00",
      "restTime": 0
    }
  ]
}
```

#### 정책

- 근무자만 호출할 수 있다.
- 로그인한 근무자 본인에게 배정된 확정 근무 일정만 조회한다.
- `from`은 `to`보다 늦을 수 없다.
- 조회 대상은 활성 `confirmed_week_schedule`, 활성 `confirmed_schedule_assignment`, 활성 `day`, 활성 `time_detail`만 포함한다.
- 여러 사업장에 속한 근무자는 기간 내 본인 배정 일정이 사업장 구분 없이 함께 내려온다.

---

### 18-9. 사장용 사업장 기간 확정 스케줄 조회 API

사장이 자신의 사업장의 확정 근무표를 임의 기간 기준으로 조회한다. 사장 홈, 근무표 화면, 월 단위/주 단위 근무자 스케줄 조회에서 공통으로 사용할 수 있다.

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/confirmed-schedules?from={from}&to={to}` |
| 권한 | OWNER |

#### Path Variable

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| workPlaceId | Long | 사업장 ID |

#### Query Parameter

| 필드명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| from | LocalDate | O | 조회 시작일 |
| to | LocalDate | O | 조회 종료일 |

#### 성공 응답: 200 OK

```json
{
  "workPlaceId": 1,
  "from": "2026-07-01",
  "to": "2026-07-31",
  "days": [
    {
      "workDate": "2026-07-06",
      "dayName": "MONDAY",
      "timeDetails": [
        {
          "timeDetailId": 10,
          "timeName": "오픈",
          "workPartNo": 1,
          "startTime": "09:00:00",
          "closeTime": "13:00:00",
          "restTime": 0,
          "workers": [
            {
              "memberId": 3,
              "name": "김철수",
              "profileImageUrl": "https://static.example.com/profile-images/3/profile.png"
            }
          ]
        }
      ]
    }
  ]
}
```

#### 정책

- 사장만 호출할 수 있다.
- 요청자는 해당 사업장의 소유자여야 한다.
- `from`은 `to`보다 늦을 수 없다.
- 조회 대상은 활성 `confirmed_week_schedule`, 활성 `confirmed_schedule_assignment`, 활성 `day`, 활성 `time_detail`만 포함한다.
- 응답은 `workDate` 오름차순, `workPartNo` 오름차순, `startTime` 오름차순, 배정 ID 오름차순 기준으로 내려간다.
- 근무자 프로필 이미지는 활성 프로필 이미지가 있는 경우에만 `profileImageUrl`로 내려간다.
- 조회 기간에 확정 배정이 없으면 `days`는 빈 배열이다.

#### 주요 에러

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | 4001 | 날짜 파라미터가 올바르지 않음 |
| 401 | 4002 | 인증 정보 없음 또는 올바르지 않음 |
| 403 | 4003 | OWNER가 아니거나 접근 권한 없음 |
| 404 | 4004 | 사업장을 찾을 수 없음 |

---

### 18-10. 사장용 사업장 주간 확정 스케줄 조회 API

사장이 자신의 사업장의 주간 확정 근무표를 날짜와 근무 타임 기준으로 조회한다. 홈 화면의 이번 주 근무자 스케줄 조회에 사용한다.

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/work-places/{workPlaceId}/confirmed-schedules/weekly?weekStartDate={weekStartDate}` |
| 권한 | OWNER |

#### Path Variable

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| workPlaceId | Long | 사업장 ID |

#### Query Parameter

| 필드명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| weekStartDate | LocalDate | O | 조회할 주간 시작일 |

#### 성공 응답: 200 OK

```json
{
  "workPlaceId": 1,
  "weekStartDate": "2026-07-06",
  "weekEndDate": "2026-07-12",
  "days": [
    {
      "workDate": "2026-07-06",
      "dayName": "MONDAY",
      "timeDetails": [
        {
          "timeDetailId": 10,
          "timeName": "오픈",
          "workPartNo": 1,
          "startTime": "09:00:00",
          "closeTime": "13:00:00",
          "restTime": 0,
          "workers": [
            {
              "memberId": 3,
              "name": "김철수",
              "profileImageUrl": "https://static.example.com/profile-images/3/profile.png"
            }
          ]
        }
      ]
    }
  ]
}
```

#### 정책

- 사장만 호출할 수 있다.
- 요청자는 해당 사업장의 소유자여야 한다.
- `weekEndDate`는 서버가 `weekStartDate + 6일`로 계산한다.
- 조회 대상은 활성 `confirmed_week_schedule`, 활성 `confirmed_schedule_assignment`, 활성 `day`, 활성 `time_detail`만 포함한다.
- 근무자 프로필 이미지는 활성 프로필 이미지가 있는 경우에만 `profileImageUrl`로 내려간다.
- 해당 주간에 확정 배정이 없으면 `days`는 빈 배열이다.

#### 주요 에러

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | 4001 | 날짜 파라미터가 올바르지 않음 |
| 401 | 4002 | 인증 정보 없음 또는 올바르지 않음 |
| 403 | 4003 | OWNER가 아니거나 접근 권한 없음 |
| 404 | 4004 | 사업장을 찾을 수 없음 |

---

## 스케줄 조건/근무 불가 제출 최신 정책

### 스케줄 조건 생성 dueDate 정책

- `POST /api/work-places/{workPlaceId}/schedule-conditions` 요청에는 `dueDate`가 필수입니다.
- `dueDate`는 `LocalDate` 문자열이며 예시는 `2026-06-21` 형식입니다.
- `dueDate`는 오늘 날짜 이상이어야 합니다. 오늘 날짜는 허용됩니다.
- `dueDate`는 생성 대상 주차의 시작일 전까지만 설정할 수 있습니다. 예를 들어 다음 주 월요일 스케줄 조건이라면 전날 일요일까지 허용됩니다.
- 응답의 `dueDate`도 `LocalDate` 형식으로 내려갑니다.

### 스케줄 조건 초기화 API

| 항목 | 값 |
| --- | --- |
| Method | `DELETE` |
| URL | `/api/work-places/{workPlaceId}/schedule-conditions/{weekScheduleId}` |
| 인증 | 필요 |
| 권한 | OWNER |
| 성공 응답 | `204 No Content` |

#### 처리 규칙

- 사장 본인의 사업장에 속한 활성 스케줄 조건만 초기화할 수 있습니다.
- 초기화 시 `week_schedule.status`는 `DELETED`로 변경되고 `deleted_at`이 기록됩니다.
- 초기화 시 해당 주간 조건의 활성 `worker_select_submission`, `worker_unavailable_time_detail`도 `DELETED`로 변경되고 `deleted_at`이 기록됩니다.
- 초기화된 스케줄 조건과 제출 데이터는 조회/제출/자동 생성 대상에서 제외됩니다.
- 초기화 후 같은 다음 주차 스케줄 조건을 다시 생성할 수 있습니다.

#### 주요 오류

| HTTP Status | Code | 상황 |
| --- | --- | --- |
| 401 | 4001 | 인증 정보가 없거나 올바르지 않음 |
| 403 | 4003 | 해당 사업장의 사장이 아님 |
| 404 | 4004 | 사업장 또는 활성 스케줄 조건을 찾을 수 없음 |

### 근무 불가 시간 제출 마감 정책

- `POST /api/work-places/{workPlaceId}/worker-select`는 요청한 `weekScheduleId`의 `dueDate`가 지난 경우 제출할 수 없습니다.
- 마감 이후 제출 시 `400 Bad Request`와 `4000` 코드가 반환됩니다.
- 마감 이후 근무자가 뒤늦게 제출해야 한다면, 사장이 스케줄 조건을 초기화하고 조건 생성부터 다시 진행해야 합니다.

### 자동 스케줄 생성 대상 정책

- `POST /api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs`는 해당 스케줄 조건에 근무 불가 제출을 완료한 활성 근무자만 대상으로 자동 스케줄을 생성합니다.
- 제출하지 않은 근무자는 자동 스케줄 생성 대상에서 제외됩니다.
- 제출 완료 근무자가 0명인 경우 `409 Conflict`가 반환됩니다.
- 자동 생성 조건을 만족하지 못하는 경우에도 `409 Conflict`가 반환되며, 원인은 응답 `message`에 구체적으로 포함됩니다.

---

## 19. 교대/대타 요청 API

확정된 근무 배정(`confirmed_schedule_assignment`)을 기준으로 근무자가 교대 또는 대타를 요청하고, 대상 근무자 응답 후 사장이 최종 승인/거절하는 기능이다.

### 공통 정책

- 모든 URL은 `/api/*` 규칙을 따른다.
- 근무자 요청 API는 `WORKER` 권한이 필요하다.
- 사장 처리/조회 API는 `OWNER` 권한이 필요하다.
- 요청자와 대상자는 모두 해당 사업장의 승인된 활성 근무자여야 한다.
- 요청자는 본인의 활성 확정 근무 배정만 교대/대타 요청할 수 있다.
- 이미 지난 근무는 교대/대타 요청할 수 없다.
- 같은 확정 근무 배정에 대해 `REQUESTED`, `ACCEPTED_BY_TARGET` 상태의 요청이 있으면 중복 요청할 수 없다.
- 요청자는 대상 근무자가 응답하기 전(`REQUESTED`)까지만 취소할 수 있다.
- 이미 수락/거절/승인/취소된 요청은 재처리할 수 없다.
- 대상 근무자가 수락해야 사장이 최종 승인/거절할 수 있다.
- 사장이 최종 승인하면 기존 배정 row는 `DELETED` 처리되고, 변경 결과가 새 활성 배정 row로 추가된다.
- 주요 상태 변경 시 앱 내부 알림 및 FCM 발송 대상 알림이 생성된다.

### 상태 값

| status | 설명 |
| --- | --- |
| `REQUESTED` | 요청자가 교대/대타 요청을 생성한 상태 |
| `ACCEPTED_BY_TARGET` | 대상 근무자가 요청을 수락한 상태 |
| `REJECTED_BY_TARGET` | 대상 근무자가 요청을 거절한 상태 |
| `APPROVED` | 사장이 최종 승인하여 확정 스케줄에 반영된 상태 |
| `REJECTED_BY_OWNER` | 사장이 최종 거절한 상태 |
| `CANCELED` | 요청자가 대상 근무자 응답 전 취소한 상태 |

### 19-1. 대타 요청 생성 API

A 근무자가 자신의 확정 근무를 B 근무자에게 넘기기 위해 요청한다. B는 해당 날짜/시간에 기존 활성 배정이 없어야 한다.

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/work-places/{workPlaceId}/work-change-requests/substitute` |
| 권한 | `WORKER` |
| 성공 응답 | `201 Created` |

```json
{
  "requestAssignmentId": 10,
  "targetMemberId": 3,
  "reason": "개인 일정으로 대타 요청합니다."
}
```

### 19-2. 교대 요청 생성 API

A 근무자와 B 근무자가 서로의 확정 근무를 바꾸기 위해 요청한다.

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/work-places/{workPlaceId}/work-change-requests/shift-swap` |
| 권한 | `WORKER` |
| 성공 응답 | `201 Created` |

```json
{
  "requestAssignmentId": 10,
  "targetAssignmentId": 11,
  "reason": "오후 근무와 교대 요청합니다."
}
```

### 생성/상태 변경 응답 예시

```json
{
  "workChangeRequestId": 1,
  "workPlaceId": 1,
  "requestType": "SUBSTITUTE",
  "status": "REQUESTED",
  "requesterMemberId": 2,
  "targetMemberId": 3,
  "requestAssignmentId": 10,
  "targetAssignmentId": null,
  "reason": "개인 일정으로 대타 요청합니다.",
  "targetRespondedAt": null,
  "processedByMemberId": null,
  "processedAt": null,
  "canceledAt": null,
  "createdAt": "2026-07-04T17:48:20.288"
}
```

### 19-3. 근무자 요청 목록 조회 API

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/work-places/{workPlaceId}/work-change-requests?scope=SENT&page=0&size=20` |
| 권한 | `WORKER` |

| Query | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| scope | String | `SENT` | `SENT`, `RECEIVED`, `ALL` |
| page | Integer | `0` | 0 이상 |
| size | Integer | `20` | 1 이상 100 이하 |

### 19-4. 사장 요청 목록 조회 API

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/work-places/{workPlaceId}/owner/work-change-requests?page=0&size=20` |
| 권한 | `OWNER` |

### 목록 응답 예시

```json
{
  "content": [
    {
      "workChangeRequestId": 1,
      "workPlaceId": 1,
      "requestType": "SUBSTITUTE",
      "status": "REQUESTED",
      "requesterMemberId": 2,
      "targetMemberId": 3,
      "requestAssignmentId": 10,
      "targetAssignmentId": null,
      "reason": "개인 일정으로 대타 요청합니다.",
      "targetRespondedAt": null,
      "processedByMemberId": null,
      "processedAt": null,
      "canceledAt": null,
      "createdAt": "2026-07-04T17:48:20.288"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 19-5. 대상 근무자 수락 API

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/work-places/{workPlaceId}/work-change-requests/{requestId}/accept` |
| 권한 | `WORKER` |

- 요청의 `targetMemberId`와 로그인 근무자가 같아야 한다.
- `REQUESTED` 상태에서만 수락할 수 있다.
- 성공 시 상태는 `ACCEPTED_BY_TARGET`가 된다.

### 19-6. 대상 근무자 거절 API

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/work-places/{workPlaceId}/work-change-requests/{requestId}/reject` |
| 권한 | `WORKER` |

```json
{
  "reason": "해당 시간에는 근무가 어렵습니다."
}
```

### 19-7. 요청자 취소 API

| 항목 | 내용 |
| --- | --- |
| Method | `DELETE` |
| URL | `/api/work-places/{workPlaceId}/work-change-requests/{requestId}` |
| 권한 | `WORKER` |

- 요청의 `requesterMemberId`와 로그인 근무자가 같아야 한다.
- `REQUESTED` 상태에서만 취소할 수 있다.
- 성공 시 상태는 `CANCELED`가 된다.

### 19-8. 사장 최종 승인 API

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/work-places/{workPlaceId}/owner/work-change-requests/{requestId}/approve` |
| 권한 | `OWNER` |

- 요청 사업장의 소유자만 승인할 수 있다.
- `ACCEPTED_BY_TARGET` 상태에서만 승인할 수 있다.
- 대타 승인 시 요청자의 기존 배정은 `DELETED` 처리되고 대상 근무자의 새 활성 배정이 생성된다.
- 교대 승인 시 양쪽 기존 배정은 `DELETED` 처리되고 서로 바뀐 새 활성 배정이 생성된다.

### 19-9. 사장 최종 거절 API

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/work-places/{workPlaceId}/owner/work-change-requests/{requestId}/reject` |
| 권한 | `OWNER` |

```json
{
  "reason": "이번 주 근무표 확정 이후 변경이 어렵습니다."
}
```

### 주요 오류

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | 4000/4001 | 요청 필드 누락, 페이지 파라미터 오류, 자기 자신에게 요청, 지난 근무 요청 |
| 401 | 4002 | 인증 정보가 없거나 올바르지 않음 |
| 403 | 4003 | 권한 없음, 사업장 소속 승인 근무자가 아님, 요청 처리 주체가 아님 |
| 404 | 4004 | 사업장, 확정 근무 배정, 교대/대타 요청을 찾을 수 없음 |
| 409 | 4005 | 대상 근무자가 해당 시간에 이미 근무 중, 처리 중인 중복 요청, 이미 처리된 요청 |
