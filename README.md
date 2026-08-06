<div align="center">
<img width="500" alt="intro" src="https://github.com/user-attachments/assets/ead1f530-2560-4485-8257-4fd5092453d0" />

  # ⚡️ PickUp

### 피카! 맘에 드는 포켓몬 카드를 픽업!

인증기관 감정 정보를 기반으로 중고 포켓몬(TCG) 카드를 실시간 경매로 거래하는 플랫폼입니다.

<p>
<a href="https://artistic-emery-094.notion.site/Softeer8th-55TD-39d2861022fb80ba9396fa43a9fcdece"><img src="https://img.shields.io/badge/팀_노션-000000?style=for-the-badge&logo=notion&logoColor=white" alt="Notion" /></a>
<a href="https://app.notion.com/p/39e2861022fb80c4bc11fd5c37e23650?v=39f2861022fb80c78030000cd935ebb2&source=copy_link"><img src="https://img.shields.io/badge/데일리스크럼-181717?style=for-the-badge&logo=notion&logoColor=white" alt="Wiki" /></a>
</p>

</div>

---

## 📋 기획
포켓몬 트레이딩 카드 중고 경매 서비스, PickUp은 크게 **구매자**와 **셀러** 두 역할을 중심으로 기능이 구성되어 있습니다.

<a href="https://artistic-emery-094.notion.site/3a22861022fb80de82fbe11db8077ec5"><img src="https://img.shields.io/badge/요구사항_명세서-000000?style=for-the-badge&logo=notion&logoColor=white" alt="Notion" /></a>
<a href="https://www.figma.com/design/X8rBmtMD8DAyKOjz07tRpA/Softeer5-55TD?node-id=0-1&t=B5vdByJBCjBul7vp-1"><img src="https://img.shields.io/badge/와이어프레임-000000?style=for-the-badge&logo=figma&logoColor=white" alt="Figma" /></a>
- **회원**: 로그인/회원가입, 구매자·셀러 역할 전환, 닉네임/비밀번호 변경, 탈퇴
- **경매 탐색 (구매자)**: 홈 대표 경매 노출, 진행 중/예정/종료 필터, 검색·정렬, 관심 등록
- **경매 상세 (구매자)**: 감정 등급·현재가·최소 입찰 단위 등 상세 정보, 실시간 현재가·남은 시간 확인
- **실시간 경매 (구매자)**: 추천 금액/직접 입력 입찰, 입찰 확인, 추월 알림, 최근 입찰 내역, 낙찰·유찰 결과 확인
- **관심·입찰 내역 (구매자)**: 관심 상품 목록 관리, 참여한 경매의 입찰 내역·낙찰 내역 조회
- **상품 관리 (셀러)**: 카드 정보 등록(검수·감정 등급, 실물 이미지), 상품 수정/삭제
- **경매 등록 (셀러)**: 희망 시작가·최소 희망 낙찰가·희망 일정 입력 후 경매 신청, 유찰 상품 재신청
- **판매 내역 (셀러)**: 신청한 경매 진행 현황 및 정산 예정 내역 조회

## 🛠 기술 스택

### Frontend

| 구분 | 기술 |
| --- | --- |
| Core | React 19, TypeScript, Vite |
| Routing / Data | TanStack Router, TanStack Query |
| API 코드 생성 | Orval (OpenAPI) |
| Styling / UI | Tailwind CSS v4, shadcn/ui |
| Package Manager | pnpm |

### Backend

| 구분 | 기술 |
| --- | --- |
| Language / Framework | Java 21, Spring Boot |
| Data | Spring Data JPA, QueryDSL, Flyway, MySQL |
| 인증 | JWT (jjwt), BCrypt |
| 문서화 | Swagger (springdoc-openapi) |
| 테스트 | JUnit5, AssertJ |

### Collaboration & Infra

| 구분 | 기술 |
| --- | --- |
| 협업 | Jira, GitHub, Slack, Notion |
| CI/CD | GitHub Actions |

## 💻 설계
### ERD
### 시스템 아키텍처
<div align="center">
<img width="700" alt="시스템아키텍처" src="https://github.com/user-attachments/assets/1d6e88a3-74a0-40d2-b5c0-3748dff337e2" />
</div>

## 👥 팀원 소개

| <img width="120" alt="image" src="https://github.com/user-attachments/assets/0d23c4bd-f635-4b98-ac9e-8e8ceceb5517" /> | <img width="120" alt="image" src="https://github.com/user-attachments/assets/5edf3ee4-b709-4800-a215-02dc97f2eff7" />| <img width="120" alt="image" src="https://github.com/user-attachments/assets/2c8eb6f2-f469-4cf3-9331-dafc393dcc02" /> | <img width="120" src="https://github.com/user-attachments/assets/bf70f820-7d19-4c3e-9c06-e6800efd2547" /> |
| :---: | :---: | :---: | :---: |
| 강민제 | 임기범 | 채주혁 | 홍지형 |
| [@10000Je](https://github.com/10000Je) | [@delphox60](https://github.com/delphox60) | [@Juhye0k](https://github.com/Juhye0k) | [@topograp2](https://github.com/topograp2) |

## 📚 그라운드 룰
### 1️⃣ **협업 방식 및 일정**

- **스프린트 단위**로 개발 진행
    - 백로그 및 주차별 개발 범위 명확히 설정
- 스프린트 종료 시 **배포 및 주간 회고** 진행
- **데일리 스크럼**
    - 매일 아침 5~15분
    - 어제 완료한 작업, 오늘 진행할 작업
    - 작업 중 막힌 부분, 논의가 필요한 사항
- 스프린트 회고
    - 목표 달성 여부 확인, 완료하지 못한 이슈 확인
    - 잘한 점, 어려웠던 점, 반복하지 않아야 할 문제
    - 다음 스프린트에서 바꿀 행동
- 슬랙
    - 프로젝트 관련 논의는 스레드 내 작성
    - 일상 등의 이야기는 5조 채널 이용, CI/CD 구축 후 배포 알림은 다른 채널 활용
- 회의 진행
    - 대면회의를 추구하고 비대면 회의는 지양
        - 갑작스럽게 버그 발생할 경우는 제외
        - 주말에 회의할 일을 만들지 않도록 하기
    - 부득이한 비대면 회의에서는 캠과 마이크를 키고 진행
    - 안건 별로 30분 타이머 맞춘 후, 10분 휴식시간을 가진 후 회의 재개

### 2️⃣ **커뮤니케이션**

- 의견 충돌을 피하지 않되, 부정적이고 감정적인 표현보다 해결 방법을 중심으로 대화한다.
- 질문이나 도움이 필요한 상황을 부담으로 생각하지 않고 적극적으로 공유한다.
- 매일 회고 시 1일 1칭찬을 진행한다.
    - 한 명이 나머지 3명 각각 한 가지씩 생각해서 말하기
    - 개발과 무관한 일상적인 칭찬도 괜찮음
- 15:00 ~ 15:30에는 잠시 쉬는 시간을 갖고 친목도모를 위한 갈틱폰 등의 게임을 진행한다.
- 개발을 제외한 의사결정은 게임을 통해 결정한다.
    - ex) 스위치 게임, 보드게임
