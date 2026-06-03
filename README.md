[ SWYP 앱 5기 5팀 프로젝트 ]

## 프로젝트 소개

AutoSchedule은 일정 관리 및 자동 스케줄링 기능을 중심으로 하는 앱 서비스입니다.  
본 레포지토리는 AutoSchedule 서비스의 백엔드 API 서버를 관리합니다.

현재 프로젝트명은 가칭이며, 추후 서비스명 확정에 따라 변경될 수 있습니다.

---
## 기술 스택

### Language

- Java 21

### Framework

- Spring Boot 3.5.14
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation

### Database / Cache

- MySQL
- Redis

### Authentication

- Spring Security
- JWT

### API Documentation

- SpringDoc OpenAPI / Swagger UI

### Monitoring

- Spring Boot Actuator
- Micrometer Prometheus Registry
- Grafana Dashboard

### Test

- JUnit 5
- Spring Boot Test
- Spring MVC Test / MockMvc
- Spring Security Test
- AssertJ
- Mockito
- Testcontainers

---

## API 문서

서버 실행 후 Swagger UI에서 API 문서를 확인할 수 있습니다.

```text
http://localhost:8080/swagger-ui/index.html
```

---


## 브랜치 전략

본 프로젝트는 단순한 Git Flow 전략을 사용합니다.

| 브랜치        | 설명           |
| ---------- | ------------ |
| main       | 배포 가능한 안정 버전 |
| develop    | 개발 통합 브랜치    |
| feature/*  | 기능 개발 브랜치    |

브랜치 예시:

```text
feature/1-member-login
```

---

## 커밋 컨벤션

| 타입       | 설명            |
| -------- | ------------- |
| feat     | 새로운 기능 추가     |
| fix      | 버그 수정         |
| refactor | 리팩터링          |
| docs     | 문서 수정         |
| test     | 테스트 코드 추가/수정  |
| chore    | 빌드, 설정, 기타 작업 |
| style    | 코드 포맷팅        |
| perf     | 성능 개선         |

커밋 예시:

```bash
git commit -m "feat: add member login api"
git commit -m "fix: resolve token validation error"
git commit -m "docs: update README"
```


