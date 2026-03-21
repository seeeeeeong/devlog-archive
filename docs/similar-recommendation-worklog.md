# similar 추천 개선 작업 정리

## 1. 작업 배경

`blog-web` 게시글 상세 페이지에서 `devlog-archive`의 `/api/v1/similar` 결과를 붙여서
"함께 읽을 만한 기업 기술글"을 보여주고 있었다.  
문제는 추천 결과가 다음 두 방향으로 동시에 좋지 않았다는 점이다.

1. 연관 없는 글이 섞였다.
2. 반대로 조금만 조건을 빡세게 걸면 `[]` 빈 배열이 자주 나왔다.

즉, "아예 아무것도 안 보이는 문제"와 "보이긴 하는데 엉뚱한 글이 나오는 문제"가
같이 존재했다.

이 문서는 그 문제를 확인하고, 어떤 순서로 고쳤고, 지금 어디까지 왔는지를
상세하게 남긴 작업 기록이다.

---

## 2. 초기 구조와 한계

초기 `devlog-archive` similar 구조는 단순했다.

1. 요청으로 받은 `title + content 일부`를 임베딩한다.
2. `article_embeddings`에서 pgvector 유사도로 가까운 문서를 뽑는다.
3. 일정 threshold 이상만 반환한다.

핵심 한계는 아래와 같았다.

### 2.1 임베딩 점수 하나에 너무 의존했다

벡터 검색은 "비슷한 기술 글"은 잘 잡지만,  
"지금 이 글과 정말 같이 읽을 글"을 잡는 데는 한계가 있다.

예를 들어 아래는 서로 꽤 다르지만 같은 개발 문맥으로 묶일 수 있다.

- JPA 엔티티 설계 글
- Redis 캐시 장애 대응 글
- Kafka Consumer 운영 글

이 상태에서 threshold가 낮으면 대충 비슷한 개발 글이 섞인다.

### 2.2 제목 신호가 본문에 묻혔다

블로그 글은 본문이 길고, 특히 내 글은 코드/회고/배경 설명 비중이 큰 편이다.  
이 경우 제목이 갖고 있는 핵심 주제 신호가 본문에 묻히기 쉽다.

### 2.3 입력 텍스트 품질이 좋지 않았다

초기 `blog-web`은 추천 요청 시 본문 앞 `2000자` 정도를 그대로 잘라서 보냈다.

이 방식은 다음 상황에서 취약하다.

- 서론이 길다
- 코드블록이 앞부분에 몰려 있다
- 링크/마크다운/보일러플레이트가 많다
- 실제 핵심 주제는 중간 이후에 나온다

### 2.4 코퍼스 규모와 토픽 분포가 충분하지 않았다

운영 `devlog-archive`를 확인했을 때 문서 수는 약 `210개` 수준이었다.  
이 정도 규모는 일반적인 회사 기술블로그 모음으로는 의미가 있지만,
내 글의 주제 분포를 촘촘하게 덮기엔 부족했다.

특히 아래 계열은 후보 풀이 얇았다.

- Redis 장애 대응
- Redis Lua Script
- Kafka Consumer / 멱등성
- Outbox Pattern
- Terraform 마이그레이션
- Materialized View 운영
- t4g.micro 운영 경험

즉, 점수 함수를 잘 만들어도 후보 자체가 약하면 결과 품질은 한계가 있다.

---

## 3. 첫 번째 개선: threshold 상향 + 후보 확대 + 재정렬

가장 먼저 한 일은 "벡터 점수 하나만 믿지 말고, 넓게 뽑은 뒤 다시 정렬하자"였다.

### 3.1 바꾼 방향

초기안 대비 다음 3가지를 같이 적용했다.

1. `topK * 3` 수준의 후보를 더 넓게 뽑도록 확대
2. 최소 유사도 기준을 `0.4` 근처에서 더 보수적으로 상향
3. title/summary 키워드 overlap을 추가해서 애플리케이션 레벨에서 재정렬

### 3.2 의도

이 단계의 목적은 단순했다.

- DB는 "넓게" 뽑는다
- 앱은 "엄격하게" 고른다

즉, pgvector를 1차 후보 생성기로 쓰고,
최종 노출 순서는 lexical 신호를 섞어서 다시 결정하게 만들었다.

### 3.3 결과

이 단계에서 "연관 없는 글이 너무 많이 섞이는 문제"는 일부 줄었다.

하지만 곧 다른 문제가 드러났다.

- strict 조건을 걸수록 `[]`가 많아졌다
- 내 글들처럼 niche한 주제는 아예 후보가 전부 탈락했다

즉, 품질은 올랐지만 리콜이 크게 떨어졌다.

---

## 4. 운영 확인: 왜 `[]`가 계속 나왔는가

이 시점에서 단순 추측이 아니라 운영 데이터를 직접 확인했다.

### 4.1 `blog-api` 운영 DB 확인 결과

운영 DB 기준으로 확인한 값은 다음과 같았다.

- `posts`: 13개
- `PUBLISHED`: 7개
- `DELETED`: 6개
- `categories`: 1개
- 태그 구조: 없음

즉, `blog-api`가 비어서 추천이 안 되는 상태는 아니었다.

하지만 메타데이터가 굉장히 약했다.

- 카테고리는 사실상 단일
- 태그 없음
- 결국 추천 입력 신호는 `title + content` 거의 전부

### 4.2 `devlog-archive` 운영 데이터 확인 결과

운영 `devlog-archive` 쪽은 다음이 확인됐다.

- `articles`: 약 210개
- `article_embeddings`: 약 210개

즉, 임베딩 누락 때문에 전체가 비는 상태도 아니었다.

### 4.3 실제 발행 글로 재현한 결과

운영 `blog-api`에서 발행된 실제 글 7개를 가져와  
운영 `devlog-archive /api/v1/similar`에 그대로 넣어 재현했다.

그 결과 당시에는 대표 글들이 전부 `0건`이었다.

- 선착순 쿠폰 시스템에서 Outbox pattern을 이렇게 설계한 이유
- Kafka Consumer로 최종 발급 확정과 멱등성 보장하기
- MVIEW 운영기: 9분짜리 갱신 속도와 실시간성 문제를 함께 해결하기
- Redis 장애를 사용자 장애로 만들지 않는 법
- AWS 콘솔에서 Terraform으로 옮기면서 발견한 것들
- t4g.micro에서 살아남기
- Redis Lua Script로 선착순 쿠폰 발급 제어하기

### 4.4 이때 내린 결론

원인은 장애가 아니었다.

정확히는 아래 두 가지가 합쳐진 상태였다.

1. 코퍼스에 내 글 주제를 덮을 후보가 충분히 많지 않았다.
2. strict rerank가 그 약한 후보들까지 전부 탈락시켰다.

즉, "추천 정확도를 높이려다 리콜을 너무 희생한 상태"였다.

---

## 5. 두 번째 개선: fallback과 last-resort 추가

여기서 방향을 바꿨다.  
strict 결과가 없다고 바로 `[]`를 내보내지 않고, 단계적으로 완화했다.

### 5.1 단계 구조

similar 결과 생성 단계를 3단계로 재구성했다.

1. `strict`
2. `fallback`
3. `last_resort`

### 5.2 strict

가장 엄격한 단계다.

- 벡터 점수
- 제목 overlap
- 본문 overlap
- 핵심 키워드 신호

를 함께 보고, 충분히 강한 후보만 남긴다.

### 5.3 fallback

strict가 비면 좀 더 느슨하게 다시 평가한다.

- vector 비중을 높게 둔다
- lexical score 기준을 조금 완화한다
- topic overlap이나 phrase score가 있으면 살린다

이 단계는 "완벽히 맞는 글이 없더라도, 최소한 읽을 만한 근접 후보"를
살리는 용도였다.

### 5.4 last-resort

fallback까지 비면, 정말 마지막으로 상위 벡터 후보 1건 정도를 남긴다.

이 단계는 품질보다 "섹션이 항상 비는 UX"를 줄이기 위한 안전장치였다.

### 5.5 효과

적용 후 실제 발행 글 기준으로 재확인했을 때,
기존에는 전부 `0건`이던 요청들에서 최소 `1~2건`은 반환되기 시작했다.

확인된 예시는 아래와 같았다.

- `MVIEW 운영기...` -> `1건`
- `Redis 장애를 사용자 장애로 만들지 않는 법` -> `1건`
- `AWS 콘솔에서 Terraform으로...` -> `1건`
- `t4g.micro에서 살아남기` -> `2건`
- `Outbox pattern...` -> `1건`
- `Kafka Consumer...` -> `1건`

즉, 빈 배열 문제는 많이 줄었다.

대신 또 새로운 사실이 분명해졌다.

"이제는 안 나오는 문제보다, 나오긴 하는데 얼마나 맞느냐가 더 중요하다."

---

## 6. 세 번째 개선: 요청 입력 자체를 바꿈 (`blog-web`)

추천 품질이 생각보다 입력 품질에 많이 좌우된다는 점이 분명했다.

그래서 프론트에서 추천 요청을 만드는 방식을 바꿨다.

### 6.1 기존 방식

대략 이런 식이었다.

- `title`
- `content.slice(0, 2000)`

이건 단순하고 빠르지만 품질이 약했다.

### 6.2 변경 방식

`blog-web`에서 추천용 query를 새로 조합하도록 바꿨다.

포함되는 요소는 아래와 같다.

- 제목
- `topicHints`
- 소제목(heading)
- 코드블록 제거 텍스트
- 핵심 문단

그리고 아래 노이즈를 제거했다.

- 코드 펜스
- 링크
- 이미지 문법
- 마크다운 기호
- 보일러플레이트성 문구

### 6.3 효과

이 변경으로 query embedding이 훨씬 덜 흐려졌다.

특히 아래 같은 글에서 도움이 컸다.

- 서론이 긴 글
- 코드블록이 앞부분에 있는 글
- 회고/설명 파트가 먼저 나오고 핵심 기술 토픽이 뒤에 나오는 글

---

## 7. 네 번째 개선: `blog-api`에서 topicHints 제공

입력 품질을 더 끌어올리기 위해 `blog-api`에 계산형 `topicHints`를 붙였다.

### 7.1 왜 태그 대신 계산형으로 갔는가

운영 DB에는 태그 구조가 없었다.  
새 스키마를 크게 추가하고 관리자 UI까지 붙이는 작업은 지금 단계에서 너무 컸다.

그래서 일단은 아래 전략으로 갔다.

- 저장형 태그 대신
- 응답 생성 시 `title + content`에서 토픽을 계산
- 프론트가 그 값을 추천 요청에 포함

### 7.2 추출 토픽 예시

대표적으로 아래 같은 힌트를 뽑도록 만들었다.

- Redis
- Kafka Consumer
- Outbox Pattern
- Idempotency
- Terraform
- PostgreSQL
- Materialized View
- Failure Handling
- Performance
- t4g.micro

### 7.3 효과

이제 추천 요청은 단순한 title/content가 아니라
"이 글이 대략 어떤 기술 토픽에 속하는가"를 구조적으로 포함하게 됐다.

이건 이후 `devlog-archive` 하이브리드 랭킹에서 바로 활용됐다.

---

## 8. 다섯 번째 개선: `devlog-archive` 하이브리드 재정렬

이 단계에서 추천 로직의 중심을 꽤 많이 바꿨다.

### 8.1 바뀐 핵심

기존:

- vector 후보 조회
- 앱에서 단순 재정렬

변경 후:

- vector 후보 조회
- lexical 후보도 별도로 확보
- 둘을 merge
- strict / fallback / last_resort 단계별로 재점수화

### 8.2 사용한 신호

최종 점수에는 아래 신호가 들어간다.

- vector similarity
- title overlap
- body overlap
- topic overlap
- phrase score

### 8.3 왜 lexical 후보를 별도로 가져왔는가

벡터 후보만으로는 명시 토픽이 강한 글을 놓칠 수 있다.

예를 들어 아래처럼 topic 단어는 정확히 맞는데,
벡터 근접도는 애매한 경우가 있다.

- query: Redis 장애 대응
- candidate: Redis failover 운영 경험

이런 후보는 lexical 후보 풀을 섞어야 살리기 쉽다.

### 8.4 운영 지표도 추가

이 단계에서 Micrometer 지표도 넣었다.

- `similar.requests` with `stage`
- `similar.top_vector`
- `similar.result_count`
- `similar.vector_candidate_count`
- `similar.lexical_candidate_count`

이유는 단순하다.  
추천은 감으로만 조정하면 끝이 없다.

실제 운영에서 아래를 봐야 다음 조정이 가능하다.

- strict hit rate
- fallback 비율
- last_resort 비율
- topVector 분포

---

## 9. 여섯 번째 개선: UI 문구 정리 (`blog-web`)

UI 문구도 조정했다.

### 9.1 바꾼 이유

추천 품질이 아직 완벽하지 않은 상태에서  
"정확히 관련된 글"처럼 읽히는 문구는 기대치를 높인다.

그래서 제목 문구를 더 완만하게 가져갔다.

- 기존: 관련 기업 기술글
- 변경: 함께 읽을 만한 기업 기술글

그리고 한때 아래 설명 문구도 붙였다.

`제목, 핵심 토픽, 본문 문맥이 겹치는 글을 우선 보여줍니다.`

하지만 이 문구는 지금 기준으로 설명 과잉이었다.  
그래서 현재는 제거한 상태다.

즉, 지금 `blog-web` UI에는 그 안내 문구가 없다.

---

## 10. 현재 추가로 진행 중인 작업: archive 문서 자체에도 topicHints 저장

여기까지 해도 한계가 남았다.

문제는 현재 `topicHints`가 주로 "내 글(query)" 쪽에 더 강하게 붙어 있고,
archive 문서(candidate) 쪽은 title/summary 기반 lexical 추정에 더 의존한다는 점이다.

그래서 다음 단계로 아래 작업을 로컬에서 진행 중이다.

### 10.1 목표

`articles` 자체에 `topic_hints`를 저장하고,  
후보 문서 점수 계산에 직접 사용한다.

### 10.2 진행 중인 내용

- `articles.topic_hints` 컬럼 추가
- 크롤링 저장 시 topic hints 추출 후 저장
- 임베딩 재시도 시 topic hints를 embedding text에도 반영
- 기존 문서는 스케줄러로 backfill
- similar 후보 점수 계산 시 candidate topic hints 반영

### 10.3 기대 효과

이 작업이 마무리되면 후보 문서도 아래 같은 구조적 신호를 가지게 된다.

- Redis
- Kafka Consumer
- Outbox Pattern
- Failure Handling
- Terraform

그러면 query 측 `topicHints`와 candidate 측 `topicHints`가 직접 맞물리기 때문에
현재보다 "명시 토픽 매칭"이 더 강해진다.

---

## 11. 실제 커밋 및 배포 이력

이번 추천 개선 흐름에서 주요 커밋은 아래와 같다.

### 11.1 `devlog-archive`

- `60dea8b7c79085ffa872133a0f46ca9195830892`
  - `fix(similar): rerank related articles`
- `74f1b216f1162ecb4e195017563396d03496ecfa`
  - `fix: harden embedding response and deploy recreate`
- `adbd61962232f1b1de731fbad673c5526e319714`
  - `fix(similar): add relaxed fallback ranking`
- `f5bc5d03241b03c12973c25c8a9c3b2f6583e799`
  - `fix(similar): add last resort match fallback`
- `8e14ad6ed5446245fe52f037d97349e615a4fb6c`
  - `feat(similar): add hybrid reranking and metrics`

### 11.2 `blog-api`

- `ff7b5e33e26fd3ade2a11e2855ef0b8d961af0cc`
  - `feat(post): expose topic hints for recommendations`

### 11.3 `blog-web`

- `7ddc00c1a96ef82cef6d3d2d4a6ce9fc18b6ad25`
  - `feat(similar): send richer recommendation queries`

### 11.4 확인한 배포 상태

`2026-03-19` 기준으로 아래 run들이 성공한 것을 확인했다.

- `devlog-archive`
  - run `23299276785`
- `blog-api`
  - run `23299276585`
- `blog-web`
  - run `23299276491`

즉, topicHints 기반 query 강화와 hybrid reranking까지는 운영 반영이 끝난 상태다.

---

## 12. 지금도 남아 있는 한계

현재 상태는 초기보다 확실히 좋아졌지만, 아직 구조적 한계가 있다.

### 12.1 코퍼스가 절대적으로 크지 않다

`210개` 안팎의 문서로는 특정 niche 주제를 충분히 덮기 어렵다.

추천 시스템에서 가장 강한 개선은 결국 후보 풀 자체를 넓히는 것이다.

### 12.2 archive 쪽 메타데이터가 아직 약하다

candidate 문서에 구조적인 topic signal이 아직 완전히 저장/활용되진 않았다.

### 12.3 last-resort는 품질보다 UX 보완용이다

last-resort는 빈 섹션을 줄이는 데는 유용하지만,
"정말 강하게 연관된 추천"을 보장하지는 않는다.

즉, 지금 화면에서 보이는 결과 중 일부는
"최적 추천"보다 "최소한 보여줄 만한 후보"에 가깝다.

---

## 13. 다음 우선순위

지금 기준으로 다음 우선순위는 아래가 가장 현실적이다.

1. archive 문서 `topic_hints` 저장/활용 마무리
2. archive 코퍼스 확장
3. 필요하면 PostgreSQL `tsvector` 또는 trigram 기반 lexical 검색 강화
4. 점수가 너무 약한 경우는 섹션을 숨길지, 1건이라도 유지할지 UX 정책 재결정

내 판단으로는 1번과 2번이 가장 크다.

특히 2번은 어떤 점수식보다 강력하다.  
후보 풀이 넓어지면 strict 단계에서 바로 걸리는 비율이 높아지고,
fallback / last-resort 의존도는 자연스럽게 줄어든다.

---

## 13.1 코퍼스 확장 작업

운영에서 확인한 `articles ~= 210` 규모는 추천 품질을 끌어올리기엔 작았다.  
그래서 수집 대상 블로그 자체를 늘리는 작업도 병행하기 시작했다.

추가한 대상은 "공식 피드가 실제로 살아 있는 곳"만 우선 넣었다.

- 컬리
- LY Corporation
- 데브시스터즈
- 요기요
- 무신사
- 원티드

이 확장은 [`V3__expand_blog_sources.sql`](/Users/sinseonglee/Desktop/devlog-archive/src/main/resources/db/migration/V3__expand_blog_sources.sql)에 들어 있다.

핵심 의도는 단순 문서 수 증가보다 "토픽 분포 확장"이다.

- Redis
- Kafka
- SRE
- 플랫폼 운영
- 인프라
- 프런트엔드/모바일 아키텍처

같은 주제가 더 다양한 회사 글에서 나와야 추천 후보 풀이 두꺼워진다.

즉, 비슷한 토픽의 서로 다른 표현을 더 많이 수집해야 strict 단계 품질도 올라간다.

---

## 14. 요약

이번 similar 개선은 한 번의 튜닝이 아니라 단계별 보정이었다.

1. 벡터 단독 추천의 한계를 확인했다.
2. threshold 상향과 후보 확대, lexical rerank를 넣었다.
3. 그 결과 `[]` 문제가 커져 fallback과 last-resort를 추가했다.
4. 프론트 입력 자체를 더 똑똑하게 만들었다.
5. `blog-api`에서 topic hints를 제공하게 했다.
6. `devlog-archive`를 hybrid reranking + metrics 구조로 바꿨다.
7. 현재는 archive 문서 자체에도 topic hints를 저장하는 방향으로 확장 중이다.

정리하면,

- 초반 문제는 "정확도 부족"
- 중간 문제는 "리콜 부족"
- 지금 남은 문제는 "코퍼스 부족 + candidate 메타데이터 부족"

이다.

즉, 추천 로직은 이제 어느 정도 구조를 갖췄고,  
다음 승부처는 데이터와 후보 품질이다.
