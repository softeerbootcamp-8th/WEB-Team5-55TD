import { AxiosError } from "axios";
import type { Bid } from "@/lib/types";
import { axiosInstance } from "@/api/mutator/custom-instance";

type BidStatus = "HIGHEST" | "OUTBID" | "WON";

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

interface AuctionBidListItemResponse {
  bidId: number;
  nicknameMasked: string;
  bidPrice: number;
  createdAt: string;
  isMine: boolean;
}

interface AuctionBidsPageResponse {
  hasNext: boolean;
  cursor?: string | null;
  size: number;
  items: AuctionBidListItemResponse[];
}

export interface AuctionBidsParams {
  cursor?: string;
  size?: number;
}

function toBid(item: AuctionBidListItemResponse): Bid {
  return {
    id: String(item.bidId),
    maskedNickname: item.nicknameMasked,
    amount: item.bidPrice,
    createdAt: item.createdAt,
    isMine: item.isMine,
  };
}

export async function getAuctionBids(
  auctionId: string,
  params: AuctionBidsParams = {},
): Promise<{ items: Bid[]; hasNext: boolean; cursor?: string }> {
  const { data } = await axiosInstance.get<AuctionBidsPageResponse>(
    `/auctions/${auctionId}/bids`,
    { params: { cursor: params.cursor, size: params.size ?? 20 } },
  );

  return {
    items: data.items.map(toBid),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}
