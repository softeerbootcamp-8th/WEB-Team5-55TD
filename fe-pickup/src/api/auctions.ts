import type { AuctionDetail, AuctionSummary, Grade } from "@/lib/types";
import { AuctionStatus } from "@/lib/types";
import { axiosInstance } from "@/api/mutator/custom-instance";

type ApiAuctionStatus = "SCHEDULED" | "ONGOING" | "WON" | "PASSED";

interface CardResponse {
  cardId: number;
  cardName: string;
  setName: string;
  cardNumber: string;
  language: string;
  rarity: string;
  imageUrl?: string | null;
}

interface AuctionListItemResponse {
  auctionId: number;
  consignmentId: number;
  card: CardResponse;
  grade?: string | null;
  auctionStatus: ApiAuctionStatus;
  startingPrice: number;
  currentPrice?: number | null;
  startedAt?: string | null;
  endedAt?: string | null;
  remainingSeconds?: number | null;
  watchCount: number;
  watched: boolean;
  thumbnailUrl?: string | null;
}

interface AuctionPageResponse {
  hasNext: boolean;
  cursor?: string | null;
  size: number;
  items: AuctionListItemResponse[];
}

export interface AuctionDetailView extends AuctionDetail {
  card?: CardResponse;
  cardState?: string;
  majorDefect?: string;
  inspectedAt?: string;
  /** 경매 전체의 낙찰 여부 (WON). 낙찰자가 누구인지와 무관하게 경매 자체의 결과다. */
  won: boolean;
  /** 조회자 본인이 이 경매의 낙찰자인지. */
  myBidWon: boolean;
}

export type AuctionSort =
  | "POPULAR"
  | "PRICE_ASC"
  | "PRICE_DESC"
  | "ENDING_SOON"
  | "STARTING_SOON"
  | "RECENT";

export interface AuctionSearchParams {
  q?: string;
  status: ApiAuctionStatus[];
  sort: AuctionSort;
  cursor?: string;
  size?: number;
}

function toUiStatus(status: ApiAuctionStatus): AuctionStatus {
  if (status === "SCHEDULED") return AuctionStatus.UPCOMING;
  if (status === "ONGOING") return AuctionStatus.LIVE;
  return AuctionStatus.ENDED;
}

function parseGrade(value?: string | null): Grade | undefined {
  if (!value) return undefined;
  const [agency, ...score] = value.trim().split(/\s+/);
  if (!agency || score.length === 0) return undefined;
  return { agency: agency as Grade["agency"], score: score.join(" ") };
}

export function computeEndsAt(item: {
  endedAt?: string | null;
  remainingSeconds?: number | null;
}): string | undefined {
  if (item.endedAt) {
    return item.endedAt;
  }
  if (typeof item.remainingSeconds === "number" && item.remainingSeconds >= 0) {
    return new Date(Date.now() + item.remainingSeconds * 1000).toISOString();
  }
  return undefined;
}

function toSummary(item: AuctionListItemResponse): AuctionSummary {
  return {
    id: String(item.auctionId),
    cardName: item.card.cardName,
    thumbnailUrl: item.thumbnailUrl ?? item.card.imageUrl ?? undefined,
    status: toUiStatus(item.auctionStatus),
    grade: parseGrade(item.grade),
    currentPrice: item.currentPrice ?? undefined,
    startPrice: item.startingPrice,
    endsAt: computeEndsAt(item),
    startsAt: item.startedAt ?? undefined,
    watchCount: item.watchCount,
    watched: item.watched,
  };
}

export interface CreateAuctionPayload {
  consignmentId: string;
  startingPrice: number;
  reserve: number;
  /** LocalDateTime 형식 (타임존 없이) — 예: "2026-08-01T10:00:00" */
  scheduledStartAt: string;
}

interface CreateAuctionResponse {
  auctionId: number;
  consignmentId: number;
  auctionStatus: ApiAuctionStatus;
  startingPrice: number;
  bidIncrement: number;
  startedAt?: string | null;
  endedAt?: string | null;
  winningBidId?: number | null;
  winningPrice?: number | null;
  createdAt: string;
}

export async function registerAuction(
  payload: CreateAuctionPayload,
): Promise<{ auctionId: string; bidIncrement: number }> {
  const { data } = await axiosInstance.post<CreateAuctionResponse>(
    "/auctions",
    {
      consignmentId: Number(payload.consignmentId),
      startingPrice: payload.startingPrice,
      reserve: payload.reserve,
      scheduledStartAt: payload.scheduledStartAt,
    },
  );
  return { auctionId: String(data.auctionId), bidIncrement: data.bidIncrement };
}

export async function searchAuctions(params: AuctionSearchParams): Promise<{
  items: AuctionSummary[];
  hasNext: boolean;
  cursor?: string;
}> {
  const { data } = await axiosInstance.get<AuctionPageResponse>("/auctions", {
    params: {
      q: params.q || undefined,
      status: params.status,
      sort: params.sort,
      cursor: params.cursor,
      size: params.size ?? 20,
    },
    paramsSerializer: {
      indexes: null,
    },
  });

  return {
    items: data.items.map(toSummary),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}

/** 진행 중인 경매가 하나도 없으면(404) null을 반환한다. */
export async function getFeaturedAuction(): Promise<AuctionSummary | null> {
  try {
    const { data } =
      await axiosInstance.get<AuctionListItemResponse>("/auctions/featured");
    return toSummary(data);
  } catch (error) {
    if (
      typeof error === "object" &&
      error !== null &&
      "response" in error &&
      (error as { response?: { status?: number } }).response?.status === 404
    ) {
      return null;
    }
    throw error;
  }
}

interface CertificateResponse {
  certificateId: number;
  serialNumber: string;
  certificationBody: string;
  grade: string;
  inspectedAt?: string | null;
}

interface ConsignmentImageResponse {
  consignmentImageId: number;
  imageOrder: number;
  imageUrl: string;
}

interface AuctionDetailResponse extends AuctionListItemResponse {
  sellerNickname?: string | null;
  certificate?: CertificateResponse | null;
  images?: ConsignmentImageResponse[] | null;
  cardState?: string | null;
  majorDefect?: string | null;
  bidIncrement?: number | null;
  myBidWon?: boolean;
}

function isListItem(value: unknown): value is AuctionListItemResponse {
  if (!value || typeof value !== "object") return false;
  const item = value as Partial<AuctionListItemResponse>;
  return (
    typeof item.auctionId === "number" &&
    typeof item.startingPrice === "number" &&
    typeof item.auctionStatus === "string" &&
    !!item.card
  );
}

/** 상세 응답에만 있는 images 배열 유무로 목록 항목과 구분한다. */
function isDetailResponse(value: unknown): value is AuctionDetailResponse {
  return (
    isListItem(value) && Array.isArray((value as { images?: unknown }).images)
  );
}

function toDetail(item: AuctionDetailResponse): AuctionDetailView {
  const summary = toSummary(item);
  const grade = item.certificate
    ? {
        agency: item.certificate.certificationBody as Grade["agency"],
        score: item.certificate.grade,
        serial: item.certificate.serialNumber,
      }
    : summary.grade;

  return {
    ...summary,
    grade,
    sellerNickname: item.sellerNickname ?? undefined,
    minBidUnit: item.bidIncrement ?? Math.round(item.startingPrice * 0.05),
    images: (item.images ?? []).map((image) => image.imageUrl),
    bidCount: 0,
    card: item.card,
    cardState: item.cardState ?? undefined,
    majorDefect: item.majorDefect ?? undefined,
    inspectedAt: item.certificate?.inspectedAt ?? undefined,
    won: item.auctionStatus === "WON",
    myBidWon: item.myBidWon ?? false,
  };
}

function detailFromListItem(item: AuctionListItemResponse): AuctionDetailView {
  const summary = toSummary(item);
  return {
    ...summary,
    sellerNickname: "",
    minBidUnit: Math.round(item.startingPrice * 0.05),
    images: [item.thumbnailUrl, item.card.imageUrl].filter(
      (url): url is string => Boolean(url),
    ),
    bidCount: 0,
    card: item.card,
    won: item.auctionStatus === "WON",
    // 목록 응답에는 조회자별 낙찰 여부가 없다.
    myBidWon: false,
  };
}

/**
 * 상세 API가 풍부한 상세 응답(certificate·images·cardState 등)을 반환하는 경우와
 * 목록과 같은 중첩 응답을 반환하는 경우를 모두 지원한다. 상세 엔드포인트가 없는
 * 서버(구 브랜치)에서는 목록에서 같은 경매를 찾아 상세 화면을 구성한다.
 */
export async function getAuctionDetail(
  auctionId: string,
): Promise<AuctionDetailView> {
  try {
    const { data } = await axiosInstance.get<unknown>(`/auctions/${auctionId}`);
    if (isDetailResponse(data)) return toDetail(data);
    if (isListItem(data)) return detailFromListItem(data);
    return data as AuctionDetailView;
  } catch (error) {
    if (!(
      typeof error === "object" &&
      error !== null &&
      "response" in error &&
      (error as { response?: { status?: number } }).response?.status === 404
    )) {
      throw error;
    }

    const page = await searchAuctions({
      status: ["SCHEDULED", "ONGOING", "WON", "PASSED"],
      sort: "RECENT",
      size: 100,
    });
    const summary = page.items.find((item) => item.id === auctionId);
    if (!summary) throw error;

    return {
      ...summary,
      sellerNickname: "",
      minBidUnit: Math.round((summary.startPrice ?? 0) * 0.05),
      images: summary.thumbnailUrl ? [summary.thumbnailUrl] : [],
      bidCount: 0,
      won: false,
      myBidWon: false,
    };
  }
}
