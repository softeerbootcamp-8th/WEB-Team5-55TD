import axios from "axios";
import type { Grade } from "@/lib/types";
import type { CardState } from "@/api/generated/model";
import { ProductStatus } from "@/lib/types";
import { axiosInstance } from "@/api/mutator/custom-instance";

export type ApiConsignmentStatus = "REGISTERABLE" | "IN_AUCTION" | "SOLD";

/**
 * 연결된 경매의 상태. ConsignmentStatus는 신청~진행 중을 IN_AUCTION 하나로만 표현하므로,
 * "예정"/"진행 중" 탭 구분과 재신청 가능 여부는 이 값으로 판단한다.
 */
export type ApiAuctionSubStatus = "SCHEDULED" | "ONGOING" | "WON" | "PASSED";

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
  gradeCode: string;
  inspectedAt?: string | null;
}

interface ConsignmentListItemResponse {
  consignmentId: number;
  auctionId?: number | null;
  card: ConsignmentCardResponse;
  sellerMemberId: number;
  majorDefect?: string | null;
  status: ApiConsignmentStatus;
  auctionStatus?: ApiAuctionSubStatus | null;
  auctionStartedAt?: string | null;
  auctionEndedAt?: string | null;
  certificate: ConsignmentCertificateResponse;
  thumbnailUrl?: string | null;
}

interface CursorPageResponse<T> {
  hasNext: boolean;
  cursor?: number | null;
  size: number;
  items: T[];
}

export interface ConsignmentSummary {
  id: string;
  auctionId?: string;
  cardName: string;
  thumbnailUrl?: string;
  grade?: Grade;
  status: ProductStatus;
}

export interface ConsignmentDetail extends ConsignmentSummary {
  setName: string;
  cardNumber: string;
  language: string;
  rarity: string;
  cardState?: CardState;
  majorDefect?: string;
  images: ConsignmentImage[];
  auctionRegistered: boolean;
  gradeCode: string;
  inspectedAt: string;
}

export interface ConsignmentImage {
  consignmentImageId: number;
  imageUrl: string;
}

interface ConsignmentImageResponse {
  consignmentImageId: number;
  imageOrder: number;
  imageUrl: string;
}

interface ConsignmentDetailResponse {
  consignmentId: number;
  card: ConsignmentCardResponse;
  sellerMemberNickname: string;
  cardState?: CardState | null;
  majorDefect?: string | null;
  status: ApiConsignmentStatus;
  auctionStatus?: ApiAuctionSubStatus | null;
  auctionStartedAt?: string | null;
  auctionEndedAt?: string | null;
  certificate: ConsignmentCertificateResponse;
  images: ConsignmentImageResponse[];
  auctionRegistered: boolean;
}

function toUiStatus(
  status: ApiConsignmentStatus,
  auctionStatus?: ApiAuctionSubStatus | null,
): ProductStatus {
  switch (status) {
    case "SOLD":
      return ProductStatus.SOLD;
    case "IN_AUCTION":
      return auctionStatus === "ONGOING"
        ? ProductStatus.AUCTION_LIVE
        : ProductStatus.AUCTION_UPCOMING;
    case "REGISTERABLE":
      return auctionStatus === "PASSED"
        ? ProductStatus.REAPPLICABLE
        : ProductStatus.REGISTERABLE;
    default:
      // 백엔드가 아직 FE에 반영되지 않은 상태값을 내려줄 경우를 대비한 안전한 기본값.
      return ProductStatus.REGISTERABLE;
  }
}

function toSummary(item: ConsignmentListItemResponse): ConsignmentSummary {
  return {
    id: String(item.consignmentId),
    auctionId: item.auctionId != null ? String(item.auctionId) : undefined,
    cardName: item.card.cardName,
    thumbnailUrl: item.thumbnailUrl ?? item.card.imageUrl ?? undefined,
    grade: {
      agency: item.certificate.certificationBody as Grade["agency"],
      score: item.certificate.grade,
      serial: item.certificate.serialNumber,
    },
    status: toUiStatus(item.status, item.auctionStatus),
  };
}

function toDetail(item: ConsignmentDetailResponse): ConsignmentDetail {
  const sortedImages = item.images
    .slice()
    .sort((a, b) => a.imageOrder - b.imageOrder);
  return {
    id: String(item.consignmentId),
    cardName: item.card.cardName,
    thumbnailUrl: sortedImages[0]?.imageUrl ?? item.card.imageUrl ?? undefined,
    grade: {
      agency: item.certificate.certificationBody as Grade["agency"],
      score: item.certificate.grade,
      serial: item.certificate.serialNumber,
    },
    status: toUiStatus(item.status, item.auctionStatus),
    setName: item.card.setName,
    cardNumber: item.card.cardNumber,
    language: item.card.language,
    rarity: item.card.rarity,
    cardState: item.cardState ?? undefined,
    majorDefect: item.majorDefect ?? undefined,
    images: sortedImages.map((image) => ({
      consignmentImageId: image.consignmentImageId,
      imageUrl: image.imageUrl,
    })),
    auctionRegistered: item.auctionRegistered,
    gradeCode: item.certificate.gradeCode,
    inspectedAt: item.certificate.inspectedAt ?? "",
  };
}

async function fetchConsignmentPage(
  status: ApiConsignmentStatus,
  params?: { cursor?: number; size?: number },
) {
  const { data } = await axiosInstance.get<
    CursorPageResponse<ConsignmentListItemResponse>
  >("/consignments", {
    params: {
      status,
      cursor: params?.cursor,
      size: params?.size ?? 50,
    },
  });
  return data;
}

export async function getMyConsignments(params: {
  status: ApiConsignmentStatus;
  cursor?: number;
  size?: number;
}): Promise<{
  items: ConsignmentSummary[];
  hasNext: boolean;
  cursor?: number;
}> {
  const data = await fetchConsignmentPage(params.status, params);

  return {
    items: data.items.map(toSummary),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}

// TanStack Query 는 queryFn 이 undefined 를 반환하면 오류로 처리하므로 "없음"은 null 로 알린다.
export async function getMyConsignmentDetail(
  id: string,
): Promise<ConsignmentDetail | null> {
  try {
    const { data } = await axiosInstance.get<ConsignmentDetailResponse>(
      `/consignments/${id}`,
    );
    return toDetail(data);
  } catch (error) {
    // 다른 셀러의 상품(403)은 존재 여부까지 숨겨야 하므로 없는 상품과 같게 다룬다.
    const status = axios.isAxiosError(error)
      ? error.response?.status
      : undefined;
    if (status === 404 || status === 403) {
      return null;
    }
    throw error;
  }
}

export async function deleteMyConsignment(id: string): Promise<void> {
  await axiosInstance.delete(`/consignments/${id}`);
}
