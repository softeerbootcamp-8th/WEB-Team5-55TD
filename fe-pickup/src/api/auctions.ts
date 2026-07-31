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
}

export type AuctionSort =
  | "POPULAR"
  | "PRICE_ASC"
  | "PRICE_DESC"
  | "ENDING_SOON"
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

function toSummary(item: AuctionListItemResponse): AuctionSummary {
  return {
    id: String(item.auctionId),
    cardName: item.card.cardName,
    thumbnailUrl: item.thumbnailUrl ?? item.card.imageUrl ?? undefined,
    status: toUiStatus(item.auctionStatus),
    grade: parseGrade(item.grade),
    currentPrice: item.currentPrice ?? undefined,
    startPrice: item.startingPrice,
    endsAt: item.endedAt ?? undefined,
    startsAt: item.startedAt ?? undefined,
    watchCount: item.watchCount,
    watched: item.watched,
  };
}

export async function searchAuctions(
  params: AuctionSearchParams,
): Promise<{
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

export async function getWatchlist(): Promise<AuctionSummary[]> {
  const watchedAuctions: AuctionSummary[] = [];
  let cursor: string | undefined;

  while (true) {
    const page = await searchAuctions({
      status: ["SCHEDULED"],
      sort: "RECENT",
      cursor,
      size: 100,
    });
    watchedAuctions.push(...page.items.filter((auction) => auction.watched));

    if (!page.hasNext) return watchedAuctions;
    if (!page.cursor) {
      throw new Error("관심 목록 다음 페이지 커서가 없습니다.");
    }
    cursor = page.cursor;
  }
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
  };
}

/**
 * 상세 API가 목록과 같은 중첩 응답을 반환하는 경우와 기존 프런트 스펙 응답을
 * 모두 지원한다. 현재 서버 브랜치에 상세 엔드포인트가 없는 동안에는 목록에서
 * 같은 경매를 찾아 상세 화면을 구성한다.
 */
export async function getAuctionDetail(
  auctionId: string,
): Promise<AuctionDetailView> {
  try {
    const { data } = await axiosInstance.get<unknown>(
      `/auctions/${auctionId}`,
    );
    if (isListItem(data)) return detailFromListItem(data);
    return data as AuctionDetailView;
  } catch (error) {
    if (
      !(
        typeof error === "object" &&
        error !== null &&
        "response" in error &&
        (error as { response?: { status?: number } }).response?.status === 404
      )
    ) {
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
    };
  }
}
