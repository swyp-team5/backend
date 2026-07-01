# 로컬 Docker 실행 가이드

이 문서는 프론트엔드 개발자가 EC2 같은 외부 서버 없이 로컬 PC에서 AutoSchedule API 서버를 테스트하기 위한 실행 방법을 설명합니다.

Docker Compose는 다음 서비스를 한 번에 실행합니다.

| 서비스 | 컨테이너 포트 | 설명 |
| --- | ---: | --- |
| Spring Boot | 8080 | AutoSchedule REST API 서버 |
| MySQL | 3306 | 애플리케이션 DB |
| Redis | 6379 | Refresh Token, 초대 코드 등 Redis 저장소 |

## 보안 원칙

실제 환경변수 값과 외부 서비스 설정 파일은 GitHub에 올리지 않습니다.

프론트엔드 개발자는 백엔드 담당자에게 별도로 전달받은 `.env` 파일을 프로젝트 루트에 둔 뒤 실행합니다.

```text
autoschedule/
  .env
  docker-compose.yml
  Dockerfile
```

`.env` 파일은 `.gitignore`에 포함되어 있으므로 커밋하면 안 됩니다.

## 실행 방법

```powershell
docker compose up --build
```

백그라운드로 실행하려면 다음 명령을 사용합니다.

```powershell
docker compose up --build -d
```

API 서버가 정상 실행되면 아래 주소로 확인할 수 있습니다.

```text
http://localhost:{APP_PORT}/actuator/health
http://localhost:{APP_PORT}/swagger-ui/index.html
```

`APP_PORT` 값은 백엔드 담당자가 전달한 `.env` 파일에 정의된 값을 사용합니다.

## MySQL 비밀번호 또는 초기 데이터 변경 시 주의

MySQL Docker 이미지는 `MYSQL_ROOT_PASSWORD`를 데이터 볼륨이 처음 생성될 때만 적용합니다.

이미 `mysql-data` 볼륨이 생성된 뒤 `.env`의 `MYSQL_ROOT_PASSWORD`를 바꾸면, Spring Boot 컨테이너가 기존 MySQL 비밀번호와 다른 값으로 접속하려고 해서 아래 오류가 발생할 수 있습니다.

```text
Access denied for user 'root'
```

비밀번호, DDL, seed 데이터가 변경되었거나 로컬 DB를 처음부터 다시 만들고 싶다면 볼륨을 삭제한 뒤 다시 실행합니다.

```powershell
docker compose down -v
docker compose up --build
```

주의: `docker compose down -v`는 로컬 MySQL과 Redis 데이터를 모두 삭제합니다.

## MySQL 스키마 초기화

MySQL 컨테이너는 최초 실행 시 아래 누적 DDL 파일을 자동 실행합니다.

```text
src/main/resources/db/DDL_V7.sql
```

그리고 프론트엔드 로컬 연동 테스트에 필요한 기본 약관 데이터도 함께 적재합니다.

```text
src/main/resources/db/DML_LOCAL_SEED.sql
```

이 seed 데이터는 로컬 테스트용 약관 마스터 데이터입니다. 실제 운영 약관 내용이 아닙니다.

Docker 공식 MySQL 이미지는 `/docker-entrypoint-initdb.d` 경로의 SQL 파일을 데이터 디렉터리가 비어 있는 최초 실행 시점에만 실행합니다.

즉, `mysql-data` 볼륨이 이미 생성되어 있으면 DDL이 다시 실행되지 않습니다.

스키마를 처음부터 다시 만들고 싶다면 로컬 볼륨을 삭제한 뒤 다시 실행합니다.

```powershell
docker compose down -v
docker compose up --build
```

주의: `docker compose down -v`는 로컬 MySQL과 Redis 데이터를 모두 삭제합니다.

## 중지 방법

컨테이너만 중지하려면 다음 명령을 사용합니다.

```powershell
docker compose down
```

로컬 데이터까지 삭제할 때만 다음 명령을 사용합니다.

```powershell
docker compose down -v
```

## 프로필 이미지 S3 임시 설정

현재 로컬 Docker 패키지는 Spring Boot + MySQL + Redis를 한 번에 실행합니다. 프로필 이미지 기능은 S3 presigned URL 방식을 사용하므로, 실제 업로드 테스트를 하려면 아래 환경변수를 `.env`에 추가해야 합니다.

운영 값이나 실제 secret은 GitHub에 올리지 않습니다. 값은 백엔드 담당자가 별도로 전달합니다.

```env
AWS_REGION=ap-northeast-2
AWS_S3_PROFILE_IMAGE_BUCKET=your-profile-image-bucket
AWS_S3_PROFILE_IMAGE_PUBLIC_BASE_URL=https://your-cloudfront-domain.example.com
AWS_S3_PROFILE_IMAGE_OBJECT_KEY_PREFIX=profile-images
AWS_S3_PROFILE_IMAGE_UPLOAD_URL_EXPIRES_SECONDS=300
AWS_S3_PROFILE_IMAGE_MAX_SIZE_BYTES=10485760
```

로컬에서 아직 S3 버킷을 만들지 않았다면 위 값은 임시값으로 둘 수 있습니다. 다만 프로필 이미지 업로드 URL 발급 후 실제 S3 업로드와 확정 API까지 테스트하려면 유효한 S3 버킷, AWS 인증 정보, 버킷 권한이 필요합니다.

MySQL 최초 실행 시 적용되는 DDL 파일은 아래 누적 최종본입니다.

```text
src/main/resources/db/DDL_V7.sql
```
