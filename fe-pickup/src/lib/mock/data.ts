/**
 * 목(mock) 데이터 — 백엔드 스펙 확정 전 화면 개발용.
 * 시간 값은 모듈 로드 시각(Date.now()) 기준으로 생성해 카운트다운이 살아 있게 한다.
 * 실제 API 연동 시 이 모듈을 Orval 생성 훅 호출로 교체한다.
 */
import type {
  AuctionDetail,
  AuctionSummary,
  Bid,
  CardInfo,
  Grade,
  MyBidItem,
  User,
} from "@/lib/types";
import { AuctionStatus, GradeAgency, MyBidStatus, UserRole } from "@/lib/types";
import { minBidUnit } from "@/lib/format";

const NOW = Date.now();
const min = (n: number) => n * 60_000;
const hour = (n: number) => n * 3_600_000;
const day = (n: number) => n * 86_400_000;
const iso = (offsetMs: number) => new Date(NOW + offsetMs).toISOString();

export const currentUser: User = {
  id: "u_me",
  nickname: "pickupKim",
  role: UserRole.BUYER,
  points: 5_000_000,
};

const g = (
  agency: keyof typeof GradeAgency,
  score: string,
  serial: string,
): Grade => ({
  agency: GradeAgency[agency],
  score,
  serial,
});

interface RawAuction {
  id: string;
  cardName: string;
  grade: Grade;
  status: (typeof AuctionStatus)[keyof typeof AuctionStatus];
  startPrice: number;
  currentPrice?: number;
  endsAt?: string;
  startsAt?: string;
  watchCount: number;
  sellerNickname: string;
  bidCount?: number;
}

const RAW: RawAuction[] = [
  {
    id: "a1",
    cardName: "리자몽 (Base Set)",
    grade: g("PSA", "10", "PSA-84213907"),
    status: AuctionStatus.LIVE,
    startPrice: 1_000_000,
    currentPrice: 1_280_000,
    endsAt: iso(min(42) + 11_000),
    watchCount: 328,
    sellerNickname: "pokemart",
    bidCount: 17,
  },
  {
    id: "a2",
    cardName: "피카츄 일러스트레이터",
    grade: g("BGS", "9.5", "BGS-1120345"),
    status: AuctionStatus.LIVE,
    startPrice: 8_000_000,
    currentPrice: 9_600_000,
    endsAt: iso(min(8) + 5_000),
    watchCount: 512,
    sellerNickname: "raregrail",
    bidCount: 41,
  },
  {
    id: "a3",
    cardName: "뮤 프로모 (2000)",
    grade: g("CGC", "9", "CGC-778210"),
    status: AuctionStatus.LIVE,
    startPrice: 400_000,
    currentPrice: 455_000,
    endsAt: iso(hour(1) + min(3)),
    watchCount: 96,
    sellerNickname: "kantodex",
    bidCount: 9,
  },
  {
    id: "a4",
    cardName: "우미 (Neo Genesis) 1st Ed.",
    grade: g("PSA", "9", "PSA-77channel"),
    status: AuctionStatus.UPCOMING,
    startPrice: 300_000,
    startsAt: iso(hour(2)),
    watchCount: 141,
    sellerNickname: "johtoshop",
  },
  {
    id: "a5",
    cardName: "블래키 골드스타",
    grade: g("PSA", "10", "PSA-90011223"),
    status: AuctionStatus.UPCOMING,
    startPrice: 2_500_000,
    startsAt: iso(hour(5)),
    watchCount: 274,
    sellerNickname: "starmie",
  },
  {
    id: "a6",
    cardName: "청룡의 백기사",
    grade: g("BGS", "9", "BGS-2245781"),
    status: AuctionStatus.UPCOMING,
    startPrice: 150_000,
    startsAt: iso(day(1)),
    watchCount: 58,
    sellerNickname: "duelist",
  },
  {
    id: "a7",
    cardName: "레드아이즈 블랙메탈",
    grade: g("PSA", "8", "PSA-66120945"),
    status: AuctionStatus.UPCOMING,
    startPrice: 90_000,
    startsAt: iso(day(2) + hour(3)),
    watchCount: 33,
    sellerNickname: "duelist",
  },
  {
    id: "a8",
    cardName: "이상해꽃 (Jungle)",
    grade: g("PSA", "9", "PSA-55098211"),
    status: AuctionStatus.ENDED,
    startPrice: 200_000,
    currentPrice: 340_000,
    endsAt: iso(-hour(3)),
    watchCount: 61,
    sellerNickname: "pokemart",
    bidCount: 12,
  },
  {
    id: "a9",
    cardName: "거북왕 (Base Set) Shadowless",
    grade: g("BGS", "9.5", "BGS-3390112"),
    status: AuctionStatus.ENDED,
    startPrice: 500_000,
    currentPrice: 0, // 유찰
    endsAt: iso(-day(1)),
    watchCount: 44,
    sellerNickname: "shadow",
    bidCount: 0,
  },
];

const IMAGES = ["front", "back", "detail-1", "detail-2"];

export const auctionSummaries: AuctionSummary[] = RAW.map((r) => ({
  id: r.id,
  cardName: r.cardName,
  status: r.status,
  grade: r.grade,
  startPrice: r.startPrice,
  currentPrice: r.currentPrice,
  endsAt: r.endsAt,
  startsAt: r.startsAt,
  watchCount: r.watchCount,
}));

export const auctionDetails: Record<string, AuctionDetail> = Object.fromEntries(
  RAW.map((r) => [
    r.id,
    {
      id: r.id,
      cardName: r.cardName,
      status: r.status,
      grade: r.grade,
      startPrice: r.startPrice,
      currentPrice: r.currentPrice,
      endsAt: r.endsAt,
      startsAt: r.startsAt,
      watchCount: r.watchCount,
      sellerNickname: r.sellerNickname,
      minBidUnit: minBidUnit(r.startPrice),
      images: IMAGES.slice(0, 2 + (r.watchCount % 3)),
      bidCount: r.bidCount ?? 0,
    } satisfies AuctionDetail,
  ]),
);

/** 경매 상세의 카드 실물/메타 정보 (접이식 상세용) */
const DEFAULT_CARD_INFO: CardInfo = {
  tcg: "Pokémon",
  set: "Base Set",
  number: "4/102",
  language: "영어",
  rarity: "Holo Rare",
  condition: "양호 — 모서리 미세 마모",
  defects: "육안상 특이 결함 없음",
};

export const auctionCardInfo: Record<string, CardInfo> = {
  a1: {
    tcg: "Pokémon",
    set: "Base Set",
    number: "4/102",
    language: "영어",
    rarity: "Holo Rare",
    condition: "GEM MINT",
    defects: "육안상 특이 결함 없음",
  },
  a2: {
    tcg: "Pokémon",
    set: "CoroCoro Promo",
    number: "—",
    language: "일본어",
    rarity: "Illustrator",
    condition: "미개봉급",
    defects: "표면 미세 광택 손실",
  },
};

export function getAuctionCardInfo(id: string): CardInfo {
  return auctionCardInfo[id] ?? DEFAULT_CARD_INFO;
}

/** 실시간 입찰 내역 (a1 기준) */
export const bidsByAuction: Record<string, Bid[]> = {
  a1: [
    {
      id: "b1",
      maskedNickname: "pickupKim",
      amount: 1_280_000,
      createdAt: iso(-min(0.2)),
      isMine: true,
    },
    {
      id: "b2",
      maskedNickname: "bid***23",
      amount: 1_230_000,
      createdAt: iso(-min(1)),
    },
    {
      id: "b3",
      maskedNickname: "col***88",
      amount: 1_180_000,
      createdAt: iso(-min(2)),
    },
    {
      id: "b4",
      maskedNickname: "psa***01",
      amount: 1_130_000,
      createdAt: iso(-min(4)),
    },
    {
      id: "b5",
      maskedNickname: "bid***23",
      amount: 1_080_000,
      createdAt: iso(-min(6)),
    },
    {
      id: "b6",
      maskedNickname: "kan***12",
      amount: 1_030_000,
      createdAt: iso(-min(9)),
    },
    {
      id: "b7",
      maskedNickname: "col***88",
      amount: 1_000_000,
      createdAt: iso(-min(12)),
    },
  ],
  a2: [
    {
      id: "c1",
      maskedNickname: "gra***77",
      amount: 9_600_000,
      createdAt: iso(-min(0.5)),
    },
    {
      id: "c2",
      maskedNickname: "pickupKim",
      amount: 9_400_000,
      createdAt: iso(-min(2)),
      isMine: true,
    },
    {
      id: "c3",
      maskedNickname: "ric***05",
      amount: 9_000_000,
      createdAt: iso(-min(5)),
    },
  ],
};

/** 마이페이지 — 입찰 내역 */
export const myBids: MyBidItem[] = [
  {
    auctionId: "a1",
    cardName: "리자몽 (Base Set)",
    grade: g("PSA", "10", "PSA-84213907"),
    myBid: 1_280_000,
    currentPrice: 1_280_000,
    status: MyBidStatus.HIGHEST,
    live: true,
  },
  {
    auctionId: "a2",
    cardName: "피카츄 일러스트레이터",
    grade: g("BGS", "9.5", "BGS-1120345"),
    myBid: 9_400_000,
    currentPrice: 9_600_000,
    status: MyBidStatus.OUTBID,
    live: true,
  },
  {
    auctionId: "a8",
    cardName: "이상해꽃 (Jungle)",
    grade: g("PSA", "9", "PSA-55098211"),
    myBid: 340_000,
    currentPrice: 340_000,
    status: MyBidStatus.WON,
    live: false,
  },
];

/** 마이페이지 — 낙찰 내역 */
export const myWins: MyBidItem[] = myBids.filter(
  (b) => b.status === MyBidStatus.WON,
);

/** 관심 목록 — 예정 경매만 (DESIGN.md §7 watchlist) */
export const watchlist: AuctionSummary[] = auctionSummaries.filter(
  (a) => a.status === AuctionStatus.UPCOMING,
);

/** 대표 경매 (홈) — 관심 수 최다 진행 중 */
export const featuredAuction: AuctionSummary =
  [...auctionSummaries]
    .filter((a) => a.status === AuctionStatus.LIVE)
    .sort((a, b) => (b.watchCount ?? 0) - (a.watchCount ?? 0))[0] ??
  auctionSummaries[0];
