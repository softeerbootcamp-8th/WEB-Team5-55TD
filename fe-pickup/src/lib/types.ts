/** 백엔드 경매 API 구현 전까지 화면과 목 데이터에서 사용하는 타입. */
export const AuctionStatus = {
  LIVE: "LIVE",
  UPCOMING: "UPCOMING",
  ENDED: "ENDED",
} as const;
export type AuctionStatus = (typeof AuctionStatus)[keyof typeof AuctionStatus];

export const GradeAgency = {
  PSA: "PSA",
  BGS: "BGS",
  CGC: "CGC",
} as const;
export type GradeAgency = (typeof GradeAgency)[keyof typeof GradeAgency];

export interface Grade {
  agency?: GradeAgency;
  score?: string;
  serial?: string;
}

export interface AuctionSummary {
  id: string;
  title?: string;
  cardName: string;
  thumbnailUrl?: string;
  status: AuctionStatus;
  grade?: Grade;
  currentPrice?: number;
  startPrice?: number;
  endsAt?: string;
  startsAt?: string;
  watchCount?: number;
  watched?: boolean;
  /** 경매 목록 응답에도 함께 내려온다. */
  sellerNickname?: string;
}

export type AuctionDetail = AuctionSummary & {
  description?: string;
  sellerId?: string;
  sellerProfileImageUrl?: string;
  minBidUnit?: number;
  images?: string[];
  bidCount?: number;
};

export interface Bid {
  id: string;
  nickname: string;
  profileImageUrl?: string;
  amount: number;
  createdAt: string;
  isMine?: boolean;
}

/**
 * 프론트 전용 세션/역할 개념 — 백엔드 회원 API 에는 아직 role · points 가 없어
 * 목 데이터 및 구매자 ↔ 셀러 모드 전환 UI 를 위해 별도로 정의한다.
 */
export const UserRole = {
  BUYER: "BUYER",
  SELLER: "SELLER",
} as const;
export type UserRole = (typeof UserRole)[keyof typeof UserRole];

export interface User {
  id: string;
  nickname: string;
  role: UserRole;
  points: number; // 보유 가상 포인트(P)
  avatarUrl?: string; // 프로필 이미지 — 없으면 닉네임 이니셜 아바타로 대체
}

/** 셀러 상품 상태 (DESIGN.md §8 product list — 검수/반려 없음) */
export const ProductStatus = {
  REGISTERABLE: "REGISTERABLE", // 등록 가능
  AUCTION_UPCOMING: "AUCTION_UPCOMING", // 경매 예정
  AUCTION_LIVE: "AUCTION_LIVE", // 경매 진행 중
  SOLD: "SOLD", // 판매 완료
  REAPPLICABLE: "REAPPLICABLE", // 재신청 가능 (유찰)
} as const;
export type ProductStatus = (typeof ProductStatus)[keyof typeof ProductStatus];

/** 내 입찰 상태 (DESIGN.md §7 mypage) */
export const MyBidStatus = {
  OUTBID: "OUTBID", // 추월됨
  HIGHEST: "HIGHEST", // 최고가
  WON: "WON", // 낙찰
  LOST: "LOST", // 미낙찰
} as const;
export type MyBidStatus = (typeof MyBidStatus)[keyof typeof MyBidStatus];

/** 카드 실물/메타 상세 (등록 · 상세 화면) */
export interface CardInfo {
  tcg: string; // TCG 종류 (예: Pokémon)
  set: string; // 세트
  number: string; // 카드 번호
  language: string; // 언어
  rarity: string; // 희귀도
  condition: string; // 카드 상태
  defects?: string; // 주요 결함
}

/** 마이페이지 입찰/낙찰 내역 항목 */
export interface MyBidItem {
  auctionId: string;
  title?: string;
  cardName: string;
  thumbnailUrl?: string;
  grade?: Grade;
  myBid: number;
  currentPrice: number;
  status: MyBidStatus;
  live: boolean; // 진행 중이면 경매방 이동 가능
}
