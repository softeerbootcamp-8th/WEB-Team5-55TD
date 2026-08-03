import { AxiosError } from "axios";
import type { Grade, MyBidItem } from "@/lib/types";
import { MyBidStatus } from "@/lib/types";
import { axiosInstance } from "@/api/mutator/custom-instance";

type BidStatus = "HIGHEST" | "OUTBID" | "WON";
type ApiAuctionStatus = "SCHEDULED" | "ONGOING" | "WON" | "PASSED";

export interface PlacedBid {
  bidId: number;
  auctionId: number;
  memberId: number;
  bidPrice: number;
  bidStatus: BidStatus;
  createdAt: string;
}

export async function placeBid(
  auctionId: string,
  bidPrice: number,
): Promise<PlacedBid> {
  const { data } = await axiosInstance.post<PlacedBid>(
    `/auctions/${auctionId}/bids`,
    { bidPrice },
  );
  return data;
}

/** 백엔드가 내려주는 한글 메시지(ExceptionResponse.message)를 그대로 보여준다. */
export function getBidErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (message) return message;
  }
  return "입찰에 실패했습니다. 잠시 후 다시 시도해 주세요.";
}

interface CardResponse {
  cardId: number;
  cardName: string;
  setName: string;
  cardNumber: string;
  language: string;
  rarity: string;
  imageUrl?: string | null;
}

interface MyBidListItemResponse {
  auctionId: number;
  card: CardResponse;
  grade?: string | null;
  myBidPrice: number;
  currentPrice: number;
  status: BidStatus;
  auctionStatus: ApiAuctionStatus;
}

interface MyBidsPageResponse {
  hasNext: boolean;
  cursor?: string | null;
  size: number;
  items: MyBidListItemResponse[];
}

export interface MyBidsParams {
  cursor?: string;
  size?: number;
}

function parseGrade(value?: string | null): Grade | undefined {
  if (!value) return undefined;
  const [agency, ...score] = value.trim().split(/\s+/);
  if (!agency || score.length === 0) return undefined;
  return { agency: agency as Grade["agency"], score: score.join(" ") };
}

/**
 * 경매가 이미 종료된 뒤에는 OUTBID를 "추월됨"이 아니라 "미낙찰"로 보여준다.
 * 백엔드가 아직 경매 종료 시 낙찰 입찰을 WON으로 전환하는 배치를 구현하지 않아
 * 실제로는 status에 WON이 관측되지 않지만, 화면은 계약된 값을 그대로 대비한다.
 */
function toUiBidStatus(
  status: BidStatus,
  auctionStatus: ApiAuctionStatus,
): MyBidStatus {
  const ended = auctionStatus === "WON" || auctionStatus === "PASSED";
  if (!ended) {
    return status === "OUTBID" ? MyBidStatus.OUTBID : MyBidStatus.HIGHEST;
  }
  return status === "WON" ? MyBidStatus.WON : MyBidStatus.LOST;
}

function toMyBidItem(item: MyBidListItemResponse): MyBidItem {
  return {
    auctionId: String(item.auctionId),
    cardName: item.card.cardName,
    thumbnailUrl: item.card.imageUrl ?? undefined,
    grade: parseGrade(item.grade),
    myBid: item.myBidPrice,
    currentPrice: item.currentPrice,
    status: toUiBidStatus(item.status, item.auctionStatus),
    live: item.auctionStatus === "ONGOING",
  };
}

export async function getMyBids(params: MyBidsParams = {}): Promise<{
  items: MyBidItem[];
  hasNext: boolean;
  cursor?: string;
}> {
  const { data } = await axiosInstance.get<MyBidsPageResponse>(
    "/members/me/bids",
    { params: { cursor: params.cursor, size: params.size ?? 20 } },
  );

  return {
    items: data.items.map(toMyBidItem),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}

export async function getMyWins(params: MyBidsParams = {}): Promise<{
  items: MyBidItem[];
  hasNext: boolean;
  cursor?: string;
}> {
  const { data } = await axiosInstance.get<MyBidsPageResponse>(
    "/members/me/wins",
    { params: { cursor: params.cursor, size: params.size ?? 20 } },
  );

  return {
    items: data.items.map(toMyBidItem),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}
