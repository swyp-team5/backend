# 로컬 Docker 인프라 실행 가이드

AutoSchedule Spring Boot 애플리케이션은 각 개발자의 로컬 PC에서 직접 실행합니다.
Docker Compose는 `application-local.yml`에서 사용하는 MySQL과 Redis만 실행합니다.

## 실행되는 서비스

| 서비스 | 컨테이너 포트 | 로컬 포트 | Spring local 설정 |
| --- | ---: | ---: | --- |
| MySQL | 3306 | 23306 | `jdbc:mysql://localhost:23306/autoschedule` |
| Redis | 6379 | 26379 | `localhost:26379` |

## 실행 방법

```powershell
docker compose up -d
```

그 다음 Spring Boot를 `local` profile로 실행합니다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

IntelliJ에서 실행한다면 active profile을 `local`로 설정하면 됩니다.

## MySQL 스키마 초기화

MySQL 컨테이너는 아래 DDL 파일을 초기화 SQL로 마운트합니다.

```text
src/main/resources/db/DDL_V2.sql
```

공식 MySQL 이미지는 `/docker-entrypoint-initdb.d` 경로에 있는 SQL 파일을
MySQL 데이터 디렉터리가 비어 있을 때만 실행합니다.

즉, `mysql-data` 볼륨이 새로 만들어지는 최초 `docker compose up -d` 시점에
`DDL_V2.sql`이 자동 실행되고 테이블이 생성됩니다.

DDL이 변경되어 로컬 DB를 다시 생성해야 한다면 볼륨을 삭제한 뒤 다시 실행합니다.

```powershell
docker compose down -v
docker compose up -d
```

`docker compose down -v`는 compose 프로젝트가 관리하는 로컬 MySQL과 Redis 데이터를 삭제합니다.

## 중지 방법

```powershell
docker compose down
```

로컬 DB와 Redis 데이터를 삭제하고 싶을 때만 `docker compose down -v`를 사용하세요.
