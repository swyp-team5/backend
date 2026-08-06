# AutoSchedule Backend

사장님과 근무자가 함께 사용하는 자동 근무표 생성 서비스의 Spring Boot 백엔드입니다.

## 주요 기능

- Google, Kakao, Apple 소셜 로그인과 JWT 인증
- 사업장 생성, 크루 초대 및 근무자 관리
- 근무 조건 생성과 근무 불가 시간 제출
- 자동 스케줄 생성, 미리보기, 확정 및 수동 편집
- 교대 및 대타 요청·승인
- 공지사항, 댓글, 공감 및 이미지 첨부
- 앱 내 알림과 FCM 푸시 알림
- S3 presigned URL 기반 프로필·공지 이미지 업로드

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.14, Spring Web MVC, Spring Security |
| Persistence | Spring Data JPA, MySQL 8.4 |
| Cache | Redis 7.2 |
| Authentication | JWT, Google/Kakao/Apple Social Login |
| Infrastructure | Docker, AWS S3, Firebase Cloud Messaging |
| API Documentation | SpringDoc OpenAPI, Swagger UI |
| Test | JUnit 5, MockMvc, Mockito, AssertJ, Testcontainers |
| Monitoring | Spring Boot Actuator, Micrometer Prometheus |

## 프로젝트 구조

기능 도메인별 패키지 안에서 `controller`, `service`, `repository`, `domain`, `dto` 책임을 분리한 레이어드 아키텍처를 사용합니다.

```text
src/main/java/com/autoschedule
├── auth
├── crew
├── global
├── member
├── notice
├── notification
├── schedule
├── schedulecondition
├── terms
├── workchange
├── workerselect
└── workplace
```

## 로컬 실행

### 준비 사항

- Docker Desktop 또는 Docker Engine
- 프로젝트 환경변수가 정의된 `.env` 파일

환경변수와 외부 서비스 인증 파일은 저장소에 커밋하지 않습니다.

### Docker Compose 실행

```powershell
docker compose up --build
```

Spring Boot, MySQL, Redis가 함께 실행되며 MySQL 최초 실행 시 `DDL_V9.sql`과 로컬 seed 데이터가 적용됩니다.

```text
Health Check: http://localhost:{APP_PORT}/actuator/health
Swagger UI:  http://localhost:{APP_PORT}/swagger-ui/index.html
```

포트와 환경변수 설정을 포함한 자세한 내용은 [LOCAL_DOCKER.md](LOCAL_DOCKER.md)를 참고합니다.

## 테스트

Windows:

```powershell
.\gradlew.bat clean test
```

Unix 계열:

```bash
./gradlew clean test
```

MySQL 또는 Redis가 필요한 통합 테스트는 Testcontainers 기반으로 실행됩니다.

## API 문서

- 전체 API 명세: [API_SPEC.md](API_SPEC.md)
- 로컬 Swagger UI: `http://localhost:{APP_PORT}/swagger-ui/index.html`

모든 서비스 API는 `/api/*` 경로를 사용합니다.

## 브랜치 전략

Simple Git Flow를 사용합니다.

| 브랜치 | 용도 |
| --- | --- |
| `main` | 운영 배포 기준 브랜치 |
| `develop` | 개발 통합 브랜치 |
| `feature/*` | 기능 개발 브랜치 |

커밋 메시지는 `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`, `perf:` 등의 타입을 사용하고 본문은 한글로 작성합니다.

## 보안 원칙

- 비밀 키, 토큰, 인증서 및 실제 환경변수 파일을 커밋하지 않습니다.
- 운영 설정은 환경변수와 배포 시점의 비공개 설정 파일로 주입합니다.
- 데이터베이스 스키마의 최종 기준은 `src/main/resources/db/DDL_V9.sql`입니다.
