<div align="center">
<img width="1024" alt="intro" src="https://github.com/user-attachments/assets/6552bdbd-0f89-4333-9f68-d3205584a517" />

  # ⚡️ PickUp

### 피카! 맘에 드는 포켓몬 카드를 픽업!
PickUp은 인증기관의 감정 정보를 기반으로 중고 포켓몬 TCG 카드를 실시간 경매로 거래하는 플랫폼입니다.<br/>
구매자는 카드의 감정 등급과 실물 이미지를 확인하고 실시간으로 입찰하며, 셀러는 상품 등록부터 경매 신청과 판매 결과까지 관리할 수 있습니다.

<p>
<a href="https://pick-up.store"><img src="https://img.shields.io/badge/PickUp_바로가기-000000?style=for-the-badge&logoColor=white" alt="PickUp_바로가기" /></a>
<a href="https://artistic-emery-094.notion.site/Softeer8th-55TD-39d2861022fb80ba9396fa43a9fcdece"><img src="https://img.shields.io/badge/팀_노션-000000?style=for-the-badge&logo=notion&logoColor=white" alt="Notion" /></a>
<a href="https://app.notion.com/p/39e2861022fb80c4bc11fd5c37e23650?v=39f2861022fb80c78030000cd935ebb2&source=copy_link"><img src="https://img.shields.io/badge/데일리스크럼-181717?style=for-the-badge&logo=notion&logoColor=white" alt="데일리스크럼" /></a>
</p>
</div>

---

## 📋 주요 기능
포켓몬 트레이딩 카드 중고 경매 서비스, PickUp은 크게 **구매자**와 **셀러** 두 역할을 중심으로 기능이 구성되어 있습니다.

<a href="https://artistic-emery-094.notion.site/3a22861022fb80de82fbe11db8077ec5"><img src="https://img.shields.io/badge/요구사항_명세서-000000?style=for-the-badge&logo=notion&logoColor=white" alt="Notion" /></a>
<a href="https://www.figma.com/design/X8rBmtMD8DAyKOjz07tRpA/Softeer5-55TD?node-id=0-1&t=B5vdByJBCjBul7vp-1"><img src="https://img.shields.io/badge/와이어프레임-000000?style=for-the-badge&logo=figma&logoColor=white" alt="Figma" /></a>
### 🔎 경매 탐색 (구매자)
* 카드명 검색과 경매 상태·가격·마감 시간 기준의 필터 및 정렬을 통해 원하는 경매를 빠르게 찾을 수 있습니다.
* 경매 상세 화면에서는 카드 실물 이미지, 감정 등급, 현재가, 다음 최소 입찰가와 판매자 정보를 확인하고, 관심 경매로 등록해 다시 모아볼 수 있습니다.
<img width="1000" alt="경매 탐색" src="https://github.com/user-attachments/assets/c12ee1fd-e494-4c9a-9dfe-f04fcaadfc9e" />



### ⏳ 실시간 경매 (구매자)
* SQS FIFO 기반 비동기 입찰 처리로 요청 순서를 보장하고, Redis Pub/Sub과 WebSocket을 통해 현재가와 입찰 결과를 모든 참여자에게 실시간으로 전달합니다.
<img width="1000" alt="입찰" src="https://github.com/user-attachments/assets/caa3badb-65b2-451d-8f93-3e3f9eed3ff6" />


### 💰 포인트 관리 (구매자)
* 구매자는 입찰에 필요한 포인트를 충전하고, 포인트 사용 내역과 현재 보유 포인트를 확인할 수 있습니다.
* 또한 여러 경매에 동시에 참여하는 경우를 고려하여, 현재 최고 입찰자로 참여 중인 경매의 입찰 금액 합계를 보유 포인트에서 제외한 사용 가능 포인트를 별도로 제공합니다
<img width="1000" alt="point screen" src="https://github.com/user-attachments/assets/909aa7d3-3865-4255-8b51-002232971758" />


### ⚡️ 경매 등록 (셀러)
* 판매자는 카드 검색을 통해 상품을 선택하고, 카드 상태와 주요 결함, 감정 정보를 입력해 상품을 등록할 수 있습니다.
* 등록된 상품은 경매 이름과 설명, 시작일시를 포함하여 경매 신청할 수 있으며, 시작일시 + 7일간 경매가 진행됩니다.
<img width="1000" alt="경매 등록" src="https://github.com/user-attachments/assets/257cabe4-cce7-445b-84a0-2ab4bfa8aab4" />

## 🛠️ 기술 스택
| 구분 | 사용 기술 |
|:---:|---|
| **Backend** | ![Java 21](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot 4](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD?style=flat-square&logoColor=white) ![WebSocket](https://img.shields.io/badge/WebSocket-010101?style=flat-square&logoColor=white) ![STOMP](https://img.shields.io/badge/STOMP-6DB33F?style=flat-square&logoColor=white) ![ShedLock](https://img.shields.io/badge/ShedLock-4A4A4A?style=flat-square&logoColor=white) |
| **Frontend** | ![React](https://img.shields.io/badge/React_19-61DAFB?style=flat-square&logo=react&logoColor=black) ![TypeScript](https://img.shields.io/badge/TypeScript_5-3178C6?style=flat-square&logo=typescript&logoColor=white) ![Vite](https://img.shields.io/badge/Vite_8-646CFF?style=flat-square&logo=vite&logoColor=white) ![TanStack Query](https://img.shields.io/badge/TanStack_Query-FF4154?style=flat-square&logo=reactquery&logoColor=white) ![TanStack Router](https://img.shields.io/badge/TanStack_Router-CA4245?style=flat-square&logo=reactrouter&logoColor=white) ![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS_4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white) ![STOMP.js](https://img.shields.io/badge/STOMP.js-231F20?style=flat-square&logoColor=white) ![Orval](https://img.shields.io/badge/Orval-7C3AED?style=flat-square&logo=openapiinitiative&logoColor=white) |
| **Database & Persistence** | ![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-FF4438?style=flat-square&logo=redis&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white) |
| **Infrastructure** | ![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white) ![VPC](https://img.shields.io/badge/Amazon_VPC-8C4FFF?style=flat-square&logo=amazonwebservices&logoColor=white) ![EC2](https://img.shields.io/badge/Amazon_EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white) ![S3](https://img.shields.io/badge/Amazon_S3-569A31?style=flat-square&logo=amazons3&logoColor=white) ![CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?style=flat-square&logo=amazonwebservices&logoColor=white) ![SQS](https://img.shields.io/badge/Amazon_SQS-FF4F8B?style=flat-square&logo=amazonsqs&logoColor=white) ![Systems Manager](https://img.shields.io/badge/AWS_Systems_Manager-FF9900?style=flat-square&logo=amazonwebservices&logoColor=white) ![Datadog](https://img.shields.io/badge/Datadog-632CA6?style=flat-square&logo=datadog&logoColor=white) |
| **CI/CD** | ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white) ![AWS OIDC](https://img.shields.io/badge/AWS_OIDC-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white) ![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white) ![pnpm](https://img.shields.io/badge/pnpm-F69220?style=flat-square&logo=pnpm&logoColor=white) ![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat-square&logo=slack&logoColor=white) |
| **Testing & Quality** | ![JUnit 5](https://img.shields.io/badge/JUnit_5-25A162?style=flat-square&logo=junit5&logoColor=white) ![Vitest](https://img.shields.io/badge/Vitest-6E9F18?style=flat-square&logo=vitest&logoColor=white) ![Testing Library](https://img.shields.io/badge/Testing_Library-E33332?style=flat-square&logo=testinglibrary&logoColor=white) ![JaCoCo](https://img.shields.io/badge/JaCoCo-CB2029?style=flat-square&logoColor=white) ![ESLint](https://img.shields.io/badge/ESLint-4B32C3?style=flat-square&logo=eslint&logoColor=white) ![Spotless](https://img.shields.io/badge/Spotless-5C2D91?style=flat-square&logoColor=white) |
<br>

## 🛠️ 기술적 도전 및 해결
> 자세한 내용은 팀 WIKI에서 확인하실 수 있습니다.
<p>
<a href="https://github.com/softeerbootcamp-8th/WEB-Team5-55TD/wiki"><img src="https://img.shields.io/badge/WIKI_바로가기-000000?style=for-the-badge&logoColor=white" alt="WIKI_바로가기" /></a>

- [WebSocket STOMP로 실시간 입찰 전달하기](https://github.com/softeerbootcamp-8th/WEB-Team5-55TD/wiki/%5B%EC%B1%84%EC%A3%BC%ED%98%81%5D-WebSocket%EA%B3%BC-STOMP%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%8B%A4%EC%8B%9C%EA%B0%84-%EC%9E%85%EC%B0%B0-%EA%B5%AC%EC%A1%B0)
- [메시지 큐로 동시 입찰 요청을 안전하게 처리하기](https://github.com/softeerbootcamp-8th/WEB-Team5-55TD/wiki/%EB%A9%94%EC%8B%9C%EC%A7%80-%ED%81%90%EB%A1%9C-%EB%8F%99%EC%8B%9C-%EC%9E%85%EC%B0%B0-%EC%9A%94%EC%B2%AD%EC%9D%84-%EC%95%88%EC%A0%84%ED%95%98%EA%B2%8C-%EC%B2%98%EB%A6%AC%ED%95%98%EA%B8%B0)
- [이벤트 기반 통신으로 정산을 견고하게 처리하기](https://github.com/softeerbootcamp-8th/WEB-Team5-55TD/wiki/%EC%9D%B4%EB%B2%A4%ED%8A%B8-%EA%B8%B0%EB%B0%98-%ED%86%B5%EC%8B%A0%EC%9C%BC%EB%A1%9C-%EC%A0%95%EC%82%B0%EC%9D%84-%EA%B2%AC%EA%B3%A0%ED%95%98%EA%B2%8C-%EC%B2%98%EB%A6%AC%ED%95%98%EA%B8%B0)
- [경매 상태 전이 방식 선정과 초 단위 폴링](https://github.com/softeerbootcamp-8th/WEB-Team5-55TD/wiki/%5B%EA%B0%95%EB%AF%BC%EC%A0%9C%5D-%EA%B2%BD%EB%A7%A4-%EC%83%81%ED%83%9C-%EC%A0%84%EC%9D%B4-%EB%B0%A9%EC%8B%9D-%EC%84%A0%EC%A0%95%EA%B3%BC-%EC%B4%88-%EB%8B%A8%EC%9C%84-%ED%8F%B4%EB%A7%81)
## 💻 설계
### ERD


### 시스템 아키텍처
<div align="center">
<img width="1000" alt="시스템아키텍처" src="https://github.com/user-attachments/assets/519ba7af-6770-4f9c-b9f0-522072c993e9" />
</div>

## 👥 팀원 소개

| <img width="120" alt="image" src="https://github.com/user-attachments/assets/0d23c4bd-f635-4b98-ac9e-8e8ceceb5517" /> | <img width="120" alt="image" src="https://github.com/user-attachments/assets/5edf3ee4-b709-4800-a215-02dc97f2eff7" />| <img width="120" alt="image" src="https://github.com/user-attachments/assets/2c8eb6f2-f469-4cf3-9331-dafc393dcc02" /> | <img width="120" src="https://github.com/user-attachments/assets/bf70f820-7d19-4c3e-9c06-e6800efd2547" /> |
| :---: | :---: | :---: | :---: |
| 강민제 | 임기범 | 채주혁 | 홍지형 |
| [@10000Je](https://github.com/10000Je) | [@delphox60](https://github.com/delphox60) | [@Juhye0k](https://github.com/Juhye0k) | [@topograp2](https://github.com/topograp2) |<img width="1433" height="1120" alt="시스템아키텍처" src="https://github.com/user-attachments/assets/5f205d77-a589-4a22-8522-1af0343768e5" />
