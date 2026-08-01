import axios from "axios";
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
  majorDefect?: string;
  images: string[];
  auctionRegistered: boolean;
  gradeCode: string;
  inspectedAt: string;
}

export interface ModifyConsignmentPayload {
  majorDefect?: string;
  certificate: {
    serialNumber: string;
    certificationBody: string;
    grade: string;
    inspectedAt: string;
  };
  images: { imageUrl: string }[];
}

interface ConsignmentImageResponse {
  productImageId: number;
  imageOrder: number;
  imageUrl: string;
}

interface ConsignmentDetailResponse {
  consignmentId: number;
  card: ConsignmentCardResponse;
  sellerMemberNickname: string;
  majorDefect?: string | null;
  status: ApiConsignmentStatus;
  certificate: ConsignmentCertificateResponse;
  images: ConsignmentImageResponse[];
  auctionRegistered: boolean;
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
    auctionId: item.auctionId != null ? String(item.auctionId) : undefined,
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

function toDetail(item: ConsignmentDetailResponse): ConsignmentDetail {
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
    setName: item.card.setName,
    cardNumber: item.card.cardNumber,
    language: item.card.language,
    rarity: item.card.rarity,
    majorDefect: item.majorDefect ?? undefined,
    images: item.images
      .slice()
      .sort((a, b) => a.imageOrder - b.imageOrder)
      .map((img) => img.imageUrl),
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
}): Promise<{ items: ConsignmentSummary[]; hasNext: boolean; cursor?: number }> {
  const data = await fetchConsignmentPage(params.status, params);

  return {
    items: data.items.map(toSummary),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}

export async function getMyConsignmentDetail(
  id: string,
): Promise<ConsignmentDetail | undefined> {
  try {
    const { data } = await axiosInstance.get<ConsignmentDetailResponse>(
      `/consignments/${id}`,
    );
    return toDetail(data);
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return undefined;
    }
    throw error;
  }
}

export async function modifyMyConsignment(
  id: string,
  payload: ModifyConsignmentPayload,
): Promise<ConsignmentDetail> {
  const { data } = await axiosInstance.patch<ConsignmentDetailResponse>(
    `/consignments/${id}`,
    payload,
  );
  return toDetail(data);
}

export async function deleteMyConsignment(id: string): Promise<void> {
  await axiosInstance.delete(`/consignments/${id}`);
}
