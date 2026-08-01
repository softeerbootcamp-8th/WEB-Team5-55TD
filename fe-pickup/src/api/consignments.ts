import type { Grade } from "@/lib/types";
import { ProductStatus } from "@/lib/types";
import { axiosInstance } from "@/api/mutator/custom-instance";

export type ApiConsignmentStatus =
  | "REGISTERABLE"
  | "AUCTION_SCHEDULED"
  | "AUCTION_ONGOING"
  | "WON"
  | "PASSED";

interface ConsignmentCardResponse {
  cardId: number;
  cardName: string;
  setName: string;
  cardNumber: string;
  language: string;
  rarity: string;
  imageUrl?: string | null;
}

interface ConsignmentCertificateResponse {
  certificateId: number;
  serialNumber: string;
  certificationBody: string;
  grade: string;
  inspectedAt?: string | null;
}

interface ConsignmentListItemResponse {
  consignmentId: number;
  card: ConsignmentCardResponse;
  sellerMemberId: number;
  majorDefect?: string | null;
  status: ApiConsignmentStatus;
  certificate: ConsignmentCertificateResponse;
}

interface CursorPageResponse<T> {
  hasNext: boolean;
  cursor?: number | null;
  size: number;
  items: T[];
}

export interface ConsignmentSummary {
  id: string;
  cardName: string;
  thumbnailUrl?: string;
  grade?: Grade;
  status: ProductStatus;
}

function toUiStatus(status: ApiConsignmentStatus): ProductStatus {
  switch (status) {
    case "REGISTERABLE":
      return ProductStatus.REGISTERABLE;
    case "AUCTION_SCHEDULED":
      return ProductStatus.AUCTION_UPCOMING;
    case "AUCTION_ONGOING":
      return ProductStatus.AUCTION_LIVE;
    case "WON":
      return ProductStatus.SOLD;
    case "PASSED":
      return ProductStatus.REAPPLICABLE;
  }
}

function toSummary(item: ConsignmentListItemResponse): ConsignmentSummary {
  return {
    id: String(item.consignmentId),
    cardName: item.card.cardName,
    thumbnailUrl: item.card.imageUrl ?? undefined,
    grade: {
      agency: item.certificate.certificationBody as Grade["agency"],
      score: item.certificate.grade,
      serial: item.certificate.serialNumber,
    },
    status: toUiStatus(item.status),
  };
}

export async function getMyConsignments(params: {
  status: ApiConsignmentStatus;
  cursor?: number;
  size?: number;
}): Promise<{ items: ConsignmentSummary[]; hasNext: boolean; cursor?: number }> {
  const { data } = await axiosInstance.get<
    CursorPageResponse<ConsignmentListItemResponse>
  >("/consignments", {
    params: {
      status: params.status,
      cursor: params.cursor,
      size: params.size ?? 50,
    },
  });

  return {
    items: data.items.map(toSummary),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}
