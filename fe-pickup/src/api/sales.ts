import type { Grade } from "@/lib/types";
import { axiosInstance } from "@/api/mutator/custom-instance";

export type ApiSalesResultType = "WON" | "PASSED";

interface SalesCardResponse {
  cardId: number;
  cardName: string;
  setName: string;
  cardNumber: string;
  language: string;
  rarity: string;
  imageUrl?: string | null;
}

interface SalesHistoryItemResponse {
  auctionId: number;
  card: SalesCardResponse;
  grade?: string | null;
  winningPrice?: number | null;
  resultType: ApiSalesResultType;
}

interface SalesHistoryPageResponse {
  hasNext: boolean;
  cursor?: string | null;
  size: number;
  items: SalesHistoryItemResponse[];
}

export interface SalesHistoryItem {
  auctionId: string;
  cardName: string;
  thumbnailUrl?: string;
  grade?: Grade;
  finalPrice?: number;
  resultType: ApiSalesResultType;
}

function parseGrade(value?: string | null): Grade | undefined {
  if (!value) return undefined;
  const [agency, ...score] = value.trim().split(/\s+/);
  if (!agency || score.length === 0) return undefined;
  return { agency: agency as Grade["agency"], score: score.join(" ") };
}

function toSummary(item: SalesHistoryItemResponse): SalesHistoryItem {
  return {
    auctionId: String(item.auctionId),
    cardName: item.card.cardName,
    thumbnailUrl: item.card.imageUrl ?? undefined,
    grade: parseGrade(item.grade),
    finalPrice: item.winningPrice ?? undefined,
    resultType: item.resultType,
  };
}

export async function getMySalesHistory(params?: {
  status?: ApiSalesResultType;
  cursor?: string;
  size?: number;
}): Promise<{
  items: SalesHistoryItem[];
  hasNext: boolean;
  cursor?: string;
}> {
  const { data } = await axiosInstance.get<SalesHistoryPageResponse>(
    "/sellers/me/sales",
    {
      params: {
        status: params?.status,
        cursor: params?.cursor,
        size: params?.size ?? 20,
      },
    },
  );

  return {
    items: data.items.map(toSummary),
    hasNext: data.hasNext,
    cursor: data.cursor ?? undefined,
  };
}
