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
- 사업장 크루 초대 코드 생성
- 사업장 크루 초대 코드 수락

모든 API URL은 `/api/*` 규칙을 따른다. `/api/v1/*` 형식은 사용하지 않는다.

## 2. 공통 규칙

### 2.1 Base URL

```text
http://localhost:8080
```

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
    "detailAddress": "3층"
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

## 8. 크루 초대 DB 정책

### 8.1 crew_invitation

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

## 9. 클라이언트 플로우

### 9.1 기존 회원 로그인

1. 앱에서 소셜 SDK 로그인 수행
2. Google은 `idToken`, Kakao는 `accessToken`, Apple은 `idToken + authorizationCode` 획득
3. `POST /api/auth/social-login` 호출
4. `LOGIN_SUCCESS`이면 토큰 저장 후 홈 진입

### 9.2 신규 회원가입

1. 앱에서 소셜 SDK 로그인 수행
2. `POST /api/auth/social-login` 호출
3. `SIGNUP_REQUIRED`이면 회원가입 화면으로 이동
4. 사용자가 공통 필수 정보, 역할, 약관, 역할별 필수 정보를 입력
5. 사장님은 `POST /api/auth/signup/owner` 호출
6. 근무자는 `POST /api/auth/signup/worker` 호출
7. `LOGIN_SUCCESS`이면 토큰 저장 후 홈 진입

주의: 회원가입 API 호출 시 소셜 인증 정보를 다시 전달해야 한다.

### 9.3 크루 초대

1. 사장님이 사업장 화면에서 초대 링크 생성을 요청한다.
2. 앱은 `POST /api/work-places/{workPlaceId}/crew-invitations`를 호출한다.
3. 서버는 6자리 초대 코드와 딥링크 URL을 반환한다.
4. 사장님은 초대 링크 또는 코드를 근무자에게 전달한다.
5. 근무자는 초대 코드 화면에서 코드를 입력한다.
6. 앱은 `POST /api/crew-invitations/{inviteCode}/accept`를 호출한다.
7. 성공하면 근무자는 해당 사업장 크루로 즉시 승인된다.

### 9.4 토큰 재발급

1. access token 만료 또는 인증 실패 감지
2. 저장된 refresh token과 deviceId로 `POST /api/auth/token/refresh` 호출
3. 성공 시 access token과 refresh token을 모두 교체 저장
4. 실패 시 로컬 토큰 삭제 후 로그인 화면으로 이동

### 9.5 로그아웃

1. 저장된 refresh token과 deviceId로 `POST /api/auth/logout` 호출
2. 성공 또는 실패와 관계없이 클라이언트 로컬 토큰 삭제 권장
3. 서버는 일치하는 Redis refresh token 세션만 삭제

## 10. 현재 구현 참고사항

- 민감 정보와 배포 환경 정보는 API 명세에 기록하지 않는다.
- 배포 관련 설정은 별도 보안 채널에서 관리한다.

- 현재 회원가입 API는 요청한 `termsId`가 DB에 존재해야 한다.
- `phoneNumber`는 하이픈 없는 11자리 숫자 형식만 허용한다.
- 소셜 이메일은 provider 응답에 없을 수 있으며, 없어도 회원가입을 허용한다.
- refresh token은 RDB에 저장하지 않고 Redis에 hash로 저장한다.
- refresh token은 `deviceId`별로 독립적으로 유지한다.
- 사장님 회원가입은 최초 사업장과 사장님 crew 소속을 함께 생성한다.
- 근무자는 회원가입만으로 기본 기능 사용이 가능하며, 사업장 소속은 크루 초대 수락 흐름에서 처리한다.
- 크루 초대 코드는 Redis TTL을 사용하지만, 최종 상태와 감사 이력은 RDB `crew_invitation`에 기록한다.
