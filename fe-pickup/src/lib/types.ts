/**
 * 앱 도메인 타입.
 * Orval 생성 모델(src/api/generated/model)을 단일 출처로 재노출하고,
 * 화면에 필요한 UI 전용 타입을 확장한다. 실제 API 연동 시 mock 을 제거하고
 * 생성 훅을 사용하면 된다.
 */
export type {
  AuctionSummary,
  AuctionDetail,
  Bid,
  User,
  Grade,
} from "@/api/generated/model";
export { AuctionStatus, GradeAgency, UserRole } from "@/api/generated/model";

/** 셀러 상품 상태 (DESIGN.md §8 product list — 검수/반려 없음) */
export const ProductStatus = {
  REGISTERABLE: "REGISTERABLE", // 등록 가능
  AUCTION_UPCOMING: "AUCTION_UPCOMING", // 경매 예정
  AUCTION_LIVE: "AUCTION_LIVE", // 경매 진행 중
  SOLD: "SOLD", // 판매 완료
  REAPPLICABLE: "REAPPLICABLE", // 재신청 가능 (유찰)
} as const;
export type ProductStatus = (typeof ProductStatus)[keyof typeof ProductStatus];

/** 정산 상태 (DESIGN.md §8 sales) */
export const SettlementStatus = {
  PENDING: "PENDING", // 정산 예정
  DONE: "DONE", // 정산 완료
  REAPPLICABLE: "REAPPLICABLE", // 재신청 가능
} as const;
export type SettlementStatus =
  (typeof SettlementStatus)[keyof typeof SettlementStatus];

/** 내 입찰 상태 (DESIGN.md §7 mypage) */
export const MyBidStatus = {
  OUTBID: "OUTBID", // 추월됨
  HIGHEST: "HIGHEST", // 최고가
  WON: "WON", // 낙찰
  LOST: "LOST", // 미낙찰
} as const;
export type MyBidStatus = (typeof MyBidStatus)[keyof typeof MyBidStatus];

import type { Grade } from "@/api/generated/model";

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

/** 셀러 상품 */
export interface Product {
  id: string;
  cardName: string;
  thumbnailUrl?: string;
  grade: Grade;
  status: ProductStatus;
  card: CardInfo;
  images: string[];
  createdAt: string;
}

/** 판매 내역 항목 */
export interface SaleItem {
  id: string;
  cardName: string;
  thumbnailUrl?: string;
  grade: Grade;
  finalPrice?: number;
  status: ProductStatus | "LOST";
  settlement: SettlementStatus;
  endedAt: string;
}

/** 마이페이지 입찰/낙찰 내역 항목 */
export interface MyBidItem {
  auctionId: string;
  cardName: string;
  thumbnailUrl?: string;
  grade: Grade;
  myBid: number;
  currentPrice: number;
  status: MyBidStatus;
  live: boolean; // 진행 중이면 경매방 이동 가능
}
