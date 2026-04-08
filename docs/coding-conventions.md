# Coding Conventions

작업할 때 항상 이 문서를 먼저 확인한다.
[Hyune-c](https://github.com/Hyune-c) 의 Spring 레포 (`traveler-mileage-service`, `exception-handling` 등) 스타일을 Kotlin 으로 옮긴 것.

---

## 1. 패키지 구조

```
com.devlog.archive
├── DevlogArchiveApplication.kt
├── config/                  # 설정 + 횡단 관심사
│   ├── *Properties.kt       # @ConfigurationProperties
│   ├── WebConfig.kt
│   └── exception/           # 예외 모델 + 핸들러
│       ├── BusinessException.kt
│       ├── ErrorCode.kt
│       ├── ErrorResponse.kt
│       └── handler/
│           └── GlobalExceptionHandler.kt
├── common/                  # 진짜 공용 유틸 (StopWords 등)
├── domain/                  # 도메인 (HTTP 비의존)
│   └── <aggregate>/
│       ├── entity/          # JPA Entity
│       ├── repository/      # JpaRepository, Querydsl 인터페이스/Impl
│       ├── service/         # 비즈니스 로직 (use case 단위로 쪼갬)
│       │   └── dto/         # 서비스 입출력 DTO
│       └── scheduler/       # 스케줄러
└── web/                     # HTTP 어댑터
    └── <aggregate>/
        ├── XxxController.kt
        ├── request/         # @RequestBody, @RequestParam DTO
        └── response/        # 응답 DTO
```

**원칙**
- `domain` 은 `web` 을 모른다. 의존 방향은 `web → domain → config/common` 한 방향.
- aggregate(=도메인 묶음) 단위로 잘게 나눈다. 평탄한 feature 폴더로 모든 걸 섞지 않는다.
- Controller 는 무조건 `web/` 아래. Entity/Repository/Service 는 무조건 `domain/` 아래.

---

## 2. 네이밍

### 2.1 Service — use case 단위로 쪼갠다
하나의 클래스에 add/get/update/delete 를 다 넣지 않는다. **유스케이스 하나당 클래스 하나**가 기본.

```
ArticleAddService
ArticleGetService
ArticleModService     // modify
ArticleDeleteService
```

여러 도메인을 묶는 호출은 `XxxFacade`:
```
ArticleCreateFacade   // Article 저장 + Embedding 생성 + 로그 기록
```

검증 로직이 길면 분리:
```
ArticleAddValidator
```

> 현재 코드는 한 서비스에 여러 책임이 섞여 있는 게 많다. 새 코드는 위 규칙을 따르고, 기존 서비스는 수정할 일이 생길 때 점진적으로 쪼갠다.

### 2.2 DTO
| 위치 | 패턴 | 예시 |
|---|---|---|
| `web/.../request/` | `XxxPostRequest`, `XxxGetRequest`, `XxxModRequest` | `ArticlePostRequest` |
| `web/.../response/` | `XxxGetResponse`, `XxxPostResponse` | `ArticleGetResponse` |
| `domain/.../service/dto/` | `XxxAddDto`, `XxxModDto` | `ArticleAddDto` |

응답 DTO 는 companion object `of(...)` 팩토리로 만든다.
```kotlin
data class ArticleGetResponse(val id: UUID, val title: String) {
    companion object {
        fun of(article: Article) = ArticleGetResponse(article.id, article.title)
    }
}
```

### 2.3 Repository
- `XxxRepository` : `JpaRepository` 인터페이스
- 동적 쿼리는 Querydsl: `XxxQuerydsl` (인터페이스) + `XxxQuerydslImpl` (구현)

### 2.4 파일/클래스
- 1 파일 1 public 클래스 원칙. inline DTO 남발 금지.
- Entity 클래스명은 `Xxx` (접미사 `Entity` 안 붙임). 단, 이 프로젝트는 이미 `XxxEntity` 로 통일돼 있어 그대로 유지.

---

## 3. Kotlin 코드 스타일

### 3.1 Controller
```kotlin
@RestController
@RequestMapping("/api/v1/articles")
class ArticleController(
    private val articleGetService: ArticleGetService,
    private val articleAddService: ArticleAddService,
) {
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ArticleGetResponse =
        ArticleGetResponse.of(articleGetService.get(id))

    @PostMapping
    fun add(@Valid @RequestBody request: ArticlePostRequest): ArticlePostResponse =
        ArticlePostResponse.of(articleAddService.add(request.toDto()))
}
```
- `ResponseEntity` 는 상태코드/헤더 커스터마이즈가 필요할 때만. 기본은 그냥 DTO 반환 + `@ResponseStatus`.
- 생성자 주입만. `@Autowired` 필드 주입 금지.

### 3.2 Service
```kotlin
@Service
class ArticleAddService(
    private val articleRepository: ArticleRepository,
    private val articleAddValidator: ArticleAddValidator,
) {
    @Transactional
    fun add(dto: ArticleAddDto): UUID {
        articleAddValidator.validate(dto)
        val article = Article(dto.title, dto.url, ...)
        articleRepository.save(article)
        return article.id
    }
}
```
- 읽기 전용은 `@Transactional(readOnly = true)`.
- 트랜잭션 경계는 service 메서드. Controller/Repository 에 `@Transactional` 금지.

### 3.3 Entity
- `var` 는 변경 가능한 필드만. 나머지는 `val`.
- 변경은 메서드로: `article.updateTitle(...)`, `article.delete()`. setter 노출 금지.
- JPA 가 요구하므로 `protected` 기본 생성자 또는 Kotlin `jpa` 플러그인 활용.

### 3.4 예외
- 비즈니스 예외는 `BusinessException(ErrorCode)` 로 던진다.
- 모든 에러 상태/메시지는 `ErrorCode` enum 한 곳에서 관리.
- 글로벌 핸들러는 `config/exception/handler/GlobalExceptionHandler` 하나로 통일.

### 3.5 로깅
```kotlin
private val log = LoggerFactory.getLogger(javaClass)
```
- 클래스 안에서 `private val log` 로 선언. companion 안 씀.
- 에러 로그는 `### message={}, cause={}` 같은 식별 가능한 prefix.

### 3.6 기타
- import wildcard (`import foo.*`) 금지. 명시적으로 적는다.
- nullable 보다 default value / sealed type 우선.
- 함수 파라미터 4개 이상이면 줄바꿈, trailing comma.

---

## 4. 테스트
- `src/test/kotlin` 패키지 구조는 `main` 과 동일하게 미러링.
- 단위 테스트 클래스명: `XxxServiceTest`, `XxxControllerTest`.
- 통합/대용량 테스트는 `largetest/` 하위로 분리.

---

## 5. 새로 추가할 때 체크리스트
- [ ] Controller 는 `web/<aggregate>/` 에 있는가?
- [ ] Service/Entity/Repository 는 `domain/<aggregate>/` 에 있는가?
- [ ] Service 는 use case 단위로 쪼개졌는가? (Add/Get/Mod/Delete)
- [ ] Request/Response DTO 가 inline 이 아니라 별도 파일인가?
- [ ] 에러는 `BusinessException` + `ErrorCode` 를 통해 던지는가?
- [ ] 트랜잭션 경계가 Service 에 있는가?
- [ ] 테스트 패키지 경로가 main 과 일치하는가?
