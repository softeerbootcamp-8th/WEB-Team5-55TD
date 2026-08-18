이 문서는 Claude / Codex 등 AI 도구가 코드를 생성·수정할 때 따라야 할 규칙이다.
아래 규칙은 팀 개발 컨벤션에서 도출되었으며, **모든 코드 생성 시 반드시 준수**한다.

## 기술 스택

- Java / Spring Boot, JPA, Flyway
- 로깅: SLF4J + logback
- 테스트: JUnit5 + AssertJ
- 문서: Swagger
- 협업: JIRA + GitHub + Slack

---

## 0. 도메인 용어 (Ubiquitous Language)

코드에서 도메인을 가리킬 때는 아래 용어를 **그대로** 클래스명/필드명/메서드명에 반영한다. 새 용어가 필요하면 이 표에 먼저 추가하고 코드에 반영한다. 한글 용어는 커밋 메시지·문서·로그 메시지에, 괄호 안 영문 용어는 코드 식별자에 사용한다.

| 용어 | 정의 |
| --- | --- |
| 회원(Member) | 서비스에 가입한 사용자. 구매자 역할과 셀러 역할을 가질 수 있다. |
| 셀러(Seller) | 카드를 출품·등록하는 회원 역할. |
| 구매자(Bidder) | 경매에 참여하는 일반 구매자 역할. |
| 카드(Card) | 거래 대상이 되는 단일 TCG 카드. |
| 검수(Inspection) | 전문가가 카드의 진위·상태를 확인하고 감정 등급을 인증하는 절차. |
| 감정 등급(Grade) | 검수 결과로 부여되는 카드 상태 등급(예: PSA 10). |
| 상품(Consignment) | 셀러가 등록하고 검수를 거치는 카드 매물. 검수 상태를 가진다. |
| 경매(Auction) | 하나의 카드에 대해 정해진 시간 동안 진행되는 실시간 입찰 거래. |
| 경매 상태(Auction Status) | 예정 · 진행 중 · 종료 · 낙찰 · 유찰 · 취소와 같이 경매의 상태를 표현하는 속성. |
| 입찰(Bid) | 회원이 특정 금액으로 구매 의사를 제시하는 행위. |
| 현재가(Current Price) | 현재까지 수락된 최고 입찰 금액. |
| 최소 입찰 단위(Bid Increment) | 한 번에 올릴 수 있는 최소 금액 단위. |
| 최소 다음 입찰가(Next Min Bid) | 현재가 + 최소 입찰 단위. |
| 추월(Outbid) | 직전 최고 입찰자가 다른 입찰로 최고 지위를 잃는 사건. |
| 리저브(Reserve) | 셀러가 정한 최소 희망 낙찰가. 미달 시 유찰된다(비공개). |
| 마감 연장(Soft Close) | 마감 직전 입찰 발생 시 종료 시각을 연장하는 규칙. |
| 낙찰(Winning) | 종료 시 최고 입찰자가 카드를 확보하는 결과. |
| 유찰(Passed) | 입찰이 없거나 리저브 미달로 낙찰되지 않은 결과. |
| 관심(Watch) | 회원이 예정 경매를 저장해두는 행위. |
| 입찰 한도(Bid Limit) | 회원이 보유한 가상 포인트 기준의 입찰 가능 한도. |
| 충전(Charge) | 실제 결제 연동 없이 클릭 즉시 동기적으로 포인트를 적립하는 행위(목업). |
| 도메인 이벤트(Domain Event) | 도메인에서 일어난 사건. 발행한 쪽은 누가 소비하는지 알지 않는다. |
| 메시지 큐 이벤트(Message Queue Event) | 하나의 소비자만 처리해야 하는 도메인 이벤트(예: 경매 시작·종료). 유실이 허용되지 않아 Outbox를 거쳐 SQS FIFO 큐로 보낸다. |
| 알림 이벤트(Notification Event) | 구독한 모든 소비자가 각자 처리하는 도메인 이벤트(예: 입찰로 인한 현재가 변동). 유실이 허용되며 Redis Pub/Sub으로 즉시 발행한다. |

### 코드 반영 예시

- `Auction.bidIncrement` (❌ `bidIncrement`처럼 용어에 없는 수식어를 붙이지 않는다)
- `Auction.reservePrice`, `AuctionListItemResponse.currentPrice` — "Price" 접미사는 금액 필드 공통 컨벤션으로 유지 (`startingPrice`/`currentPrice`/`reservePrice`/`winningPrice`)
- `Consignment.sellerMember` (셀러 역할을 가진 Member 참조)
- `Watch`, `watchCount`, `watched` — 관심(Watch) 용어 그대로 사용 (❌ `AuctionWatch`처럼 용어에 없는 접두어를 붙이지 않는다)
- `Certificate`/`Grade`/`inspectedAt` — 검수(Inspection) 절차의 결과물을 표현 (검수 자체를 별도 엔티티로 만들지는 않음)
- 아직 미구현 개념(Bidder, Bid, Current Price 실값, Next Min Bid, Outbid, Soft Close, Bid Limit)을 구현할 때는 이 표의 영문 용어를 그대로 클래스/필드명으로 사용한다.

---

## 1. 패키지 구조

도메인 단위로 패키지를 구성하고, 공통 관심사는 `global`에 둔다.

```
{도메인}
 ├─ controller      // 요청/응답 처리, DTO 주고받음
 ├─ dto
 │   ├─ request
 │   └─ response
 ├─ service         // 비즈니스 로직 (도메인 관점 행위)
 ├─ repository
 │   ├─ {도메인}Repository            // 인터페이스
 │   └─ {도메인}{조회방식}Repository   // 구현체 (예: MemberJpaRepository)
 └─ domain          // 핵심 도메인 모델

global
 ├─ exception
 └─ ...
```

---

## 2. Repository vs Service 네이밍

**Repository는 영속성 관점, Service는 비즈니스 행위 관점**으로 이름을 짓는다.

- Repository 인터페이스: `{도메인}Repository`
- Repository 구현체: 조회 방식을 접두사로 (`MemberJpaRepository`, `MemberMemoryRepository`)
- Repository 메서드: `save`, `findById` 같은 영속성 네이밍
- Service 메서드: `createAuction`, `placeBid` 같은 행위 네이밍 (영속성 이름을 그대로 노출하지 않음)

```java
// Repository
public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findById(Long id);
}

// Service
public Member registerMember(RegisterMemberCommand command) { ... }
public void placeBid(Long auctionId, Long bidderId, BigDecimal bidPrice) { ... }
```

---

## 3. CRUD 메서드 네이밍 (엄격 준수)

`동작 + 대상/조건` 순서로 작성한다. 조회 조건은 `By` 뒤에 붙인다.

| 목적 | 규칙 | 예시 |
| --- | --- | --- |
| 저장/변경 반영 | `save` | `save(member)` |
| 신규 삽입만 | `insert` / `create` | `insert(bid)` |
| 선택적 단건 조회 | `findBy...` → **Optional 반환** | `findById(id)` |
| 필수 단건 조회 | `get...` → **없으면 예외** | `getMember(id)` |
| 다건 조회 | `findAllBy...` → **빈 컬렉션 반환** | `findAllByStatus(status)` |
| 존재 확인 | `existsBy...` | `existsByLoginId(loginId)` |
| 개수 조회 | `countBy...` | `countByStatus(status)` |
| 특정 필드 수정 | `update...By...` → `int` 반환 | `updateStatusById(id, status)` |
| 삭제 | `deleteBy...` | `deleteById(id)` |
| 정렬 | `OrderBy필드Asc/Desc` | `...OrderByCreatedAtDesc` |
| 제한 조회 | `findFirst`, `findTopN` | `findTop10By...` |
| 락 조회 | `findByIdForUpdate` | `findByIdForUpdate(id)` |
| 연관 조회 | `With...` (Fetch Join) | `findByIdWithSeller(id)` |

### find vs get

```
find : 결과가 없을 수 있음 → Optional 반환 (주로 Repository)
get  : 반드시 존재해야 함 → 없으면 예외 (주로 Service 계층에서 구현)
```

```java
Optional<Member> findById(Long id);                 // Repository

public Member getById(Long id) {                    // Service
    return memberRepository.findById(id)
            .orElseThrow(MemberNotFoundException::new);
}
```

### 조건 조합 / 정렬

- 조합: `And`, `Or`, `In`/`NotIn`, `Between`, `LessThan(Equal)`, `GreaterThan(Equal)`, `IsNull`/`IsNotNull`, `Like`/`Containing`
- 정렬: `OrderBy필드Asc/Desc`, **동적 정렬은 메서드명에 넣지 말고 `Sort`/`Pageable`로 전달**

### 소프트 삭제

실제 삭제가 아니라 상태 변경이면 `delete`를 쓰지 않고 비즈니스 상태 동사를 쓴다.

```
deactivate(비활성화), archive(보관), withdraw(탈퇴), cancel(취소), close(종료)
```

### 금지 네이밍

```
select, load, search(단건), read, remove, process, handle → 사용 금지
```

- `search`는 **여러 조건을 동적 조합하는 검색**에 한해 허용 (`searchProducts(condition, pageable)`)

---

## 4. DTO

- **`record`로만** 작성한다.
- `request` / `response` 패키지를 분리한다.
- 클래스명은 `~Request`, `~Response`로 끝낸다.
- 도메인 변환 로직은 DTO 내부에 둔다 (`fromEntity`).

```java
public record MemberResponse(Long id, String nickname) {
    public static MemberResponse fromEntity(Member member) {
        return new MemberResponse(member.getId(), member.getNickname());
    }
}
```

---

## 5. 예외 처리

예외는 `enum`으로 관리하며 `statusCode` / `식별 코드` / `message`를 갖는다.

```java
public enum ErrorCode {
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    AUCTION_ALREADY_CLOSED(409, "AUCTION_ALREADY_CLOSED", "이미 종료된 경매입니다.");
    // statusCode, code, message 필드
}
```

식별 코드는 `USER_NOT_FOUND`처럼 **코드만 보고도 예외를 파악**할 수 있게 짓는다.

---

## 6. Optional 사용 규칙

`Optional`은 **반환값이 없을 수 있음을 호출자에게 알리는 도구**다.

- ✅ 메서드 반환 타입으로만 사용한다.
- ❌ 클래스 필드, 메서드 매개변수에 사용하지 않는다.
- 값이 없을 때: `.orElseThrow(() -> new XxxException(...))`
- 비용 있는 기본값(객체 생성, DB 조회)은 `orElse()`가 아니라 **`orElseGet()`** 사용.

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

User user = optionalUser.orElseGet(() -> createDefaultUser());
```

---

## 7. 로그 작성 양식

로그 한 줄로 **언제 / 어디서 / 무엇을 / 누구에 대해** 를 알 수 있어야 한다.

### 규칙

- SLF4J **파라미터 바인딩(`{}`)** 사용. 문자열 결합(`+`) 금지.
- 메시지 형식: `{한글 서술} - {key}={value}, {key}={value}`
- 엔티티 관련 로그에는 **식별자(ID) 반드시 포함**.
- 요청 추적을 위해 MDC에 `requestId`(요청 진입 시 UUID 생성, 응답 종료 시 `clear()`)를 담는다.
- 민감정보(비밀번호 평문, 토큰 전체, 주민번호)는 **로그에 남기지 않고**, 이메일·전화번호·카드번호는 마스킹한다.

### logback 패턴

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%-5level] [%X{requestId}] [%thread] %logger{36} - %msg%n
```

```
2025-07-23 14:03:11.482 [INFO ] [a1b2c3d4] [http-nio-8080-exec-2] c.o.auction.AuctionService - 경매 시작 - auctionId=1024, sellerId=88
```

### 레벨 기준

- `debug`: 개발 환경 전용. **상용에 남기지 않는다.**
- `info`: 운영 로직상 알려야 하는 상태 변화 (경매 시작, 가입 완료 등).
- `warn`: 실패는 아니나 주의가 필요한 상황 (재시도, 폴백).
- `error`: 예외를 `catch`하여 처리한 경우. **Slack notify.**

### 예외 로깅

throwable은 **마지막 인자**로 넘긴다.

```java
// 권장
log.error("경매 종료 처리 실패 - auctionId={}", auctionId, e);

// 금지
log.error("에러: " + e.getMessage());   // 스택 트레이스 유실
log.error("실패 {}", e);                 // throwable을 파라미터로 소비
e.printStackTrace();
```

### 금지

- 반복문 내부 info 이상 로그
- `System.out.println`
- 빈 `catch` 블록 (로그 없이 예외 무시)

---

## 8. 테스트 작성 양식

- **JUnit5 + AssertJ**로 한정한다.
- 메서드명은 **한글 동사형(`~다`)**, 형식은 `{조건}면_{기대결과}다`.
- 본문은 `// given`, `// when`, `// then` 3단 구조로 명시한다.
- 한 테스트는 하나의 검증 목적만 다룬다.
- 예외 검증 등 실행과 단언이 붙으면 `// when & then`으로 결합한다.
- 케이스가 많으면 `@Nested` + `@DisplayName`으로 그룹핑한다.

```java
@Test
void 유효한_회원정보로_가입하면_회원이_저장된다() {
    // given
    RegisterMemberCommand command = new RegisterMemberCommand("loginId", "nickname");

    // when
    Member savedMember = memberService.registerMember(command);

    // then
    assertThat(savedMember.getId()).isNotNull();
    assertThat(savedMember.getNickname()).isEqualTo("nickname");
}

@Test
void 존재하지_않는_회원을_조회하면_예외가_발생한다() {
    // given
    Long notExistId = 999L;

    // when & then
    assertThatThrownBy(() -> memberService.getById(notExistId))
            .isInstanceOf(MemberNotFoundException.class);
}
```

- 예외: `assertThatThrownBy(...).isInstanceOf(...)`
- JUnit 기본 단언(`assertEquals` 등) 사용 금지 → AssertJ로 통일.

---

## 9. 기타 규칙

- **환경 변수**: `application.yml`(공통) / `application-dev.yml` / `application-prod.yml`로 분리.
- **DB 형상 관리**: Flyway. DDL은 멱등하게 작성 (`IF EXISTS`, `IF NOT EXISTS`).
- **Swagger**: 설정을 별도 클래스로 분리해 다른 로직과 구분.
- **식별자(ID) 타입**: `bigint` (`Long`).

---

## 10. Git / PR

### 브랜치

```
main : 운영 배포
dev  : 개발 통합
{이슈번호}/{이슈타입}/{이슈이름}   예: OOTD-123/feat/login-api
```

이슈타입 = 커밋 타입: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`

### PR 제목

```
[DEV-{jira 번호}/{파트}] {type}: {message}
예: [OOTD-26/BE] feat: dev 서버 배포 자동화 스크립트 작성
```

### 머지

- 2인 이상 코드 리뷰 후 머지.
- 머지 방식은 **Squash Merge**.

---

## 요약 체크리스트 (코드 생성 전 확인)

- [ ] 클래스/필드/메서드명이 도메인 용어(Ubiquitous Language) 표와 일치하는가
- [ ] Repository 메서드는 영속성 네이밍, Service는 행위 네이밍인가
- [ ] `find`(Optional) / `get`(예외) 구분이 정확한가
- [ ] 금지 네이밍(select, load, remove, process...)을 쓰지 않았는가
- [ ] DTO는 `record`이고 `~Request`/`~Response`로 끝나는가
- [ ] `Optional`을 필드/매개변수에 쓰지 않았는가
- [ ] 로그가 `{}` 바인딩 + `key=value` 형식이고 throwable을 마지막 인자로 넘겼는가
- [ ] 테스트 메서드명이 한글 `~다`이고 given/when/then 구조인가
- [ ] 식별자 타입이 `Long`(bigint)인가