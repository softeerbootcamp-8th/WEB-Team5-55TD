import { ProductStatus } from "@/lib/types";
import type { MyBidStatus } from "@/lib/types";

type BadgeVariant =
  "neutral" | "outline" | "live" | "warning" | "success" | "danger" | "muted";

/** 셀러 상품 상태 (DESIGN.md §8) */
export const PRODUCT_STATUS_META: Record<
  ProductStatus,
  { label: string; variant: BadgeVariant }
> = {
  REGISTERABLE: { label: "등록 가능", variant: "success" },
  AUCTION_UPCOMING: { label: "경매 예정", variant: "warning" },
  AUCTION_LIVE: { label: "경매 진행 중", variant: "live" },
  SOLD: { label: "판매 완료", variant: "neutral" },
  REAPPLICABLE: { label: "재신청 가능", variant: "outline" },
};

/** 내 입찰 상태 (DESIGN.md §7 mypage) */
export const MY_BID_STATUS_META: Record<
  MyBidStatus,
  { label: string; variant: BadgeVariant }
> = {
  OUTBID: { label: "추월됨", variant: "danger" },
  HIGHEST: { label: "최고가", variant: "success" },
  WON: { label: "낙찰", variant: "success" },
  LOST: { label: "미낙찰", variant: "neutral" },
};

export { ProductStatus };
