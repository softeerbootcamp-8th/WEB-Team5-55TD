const POKETRACE_BASE_URL = "https://api.poketrace.com/v1";

interface PokeTraceCardSummary {
  id: string;
  name: string;
  cardNumber?: string;
  set?: { name?: string };
}

interface PokeTraceCardDetail {
  id: string;
  gradedOptions?: string[];
}

interface PokeTraceHistoryRow {
  date: string;
  avg?: number | null;
  median7d?: number | null;
}

export interface GradePriceSeries {
  tier: string;
  points: Array<{ date: string; price: number }>;
}

export interface CardPriceHistory {
  cardId: string;
  tiers: string[];
}

function apiKey(): string {
  return import.meta.env.VITE_POKETRACE_API_KEY?.trim() ?? "";
}

async function request<T>(path: string, signal?: AbortSignal): Promise<T> {
  const key = apiKey();
  if (!key) throw new Error("PokeTrace API key is not configured");

  const response = await fetch(`${POKETRACE_BASE_URL}${path}`, {
    headers: { "X-API-Key": key },
    signal,
  });
  if (!response.ok)
    throw new Error(`PokeTrace request failed: ${response.status}`);
  return response.json() as Promise<T>;
}

const normalize = (value?: string) =>
  value?.toLowerCase().replace(/[^a-z0-9가-힣]/g, "") ?? "";

/** 경매 카드와 일치하는 PokeTrace 카드 및 조회 가능한 감정 등급을 찾는다. */
export async function findGradedCard(
  cardName: string,
  setName?: string,
  cardNumber?: string,
  signal?: AbortSignal,
): Promise<CardPriceHistory> {
  const params = new URLSearchParams({
    search: cardName,
    market: "US",
    product_type: "single",
    has_graded: "true",
    limit: "20",
  });
  if (cardNumber) params.set("card_number", cardNumber.split("/")[0]);

  const result = await request<{ data?: PokeTraceCardSummary[] }>(
    `/cards?${params}`,
    signal,
  );
  const candidates = result.data ?? [];
  const exact = candidates.find(
    (card) =>
      normalize(card.name) === normalize(cardName) &&
      (!setName || normalize(card.set?.name) === normalize(setName)) &&
      (!cardNumber ||
        normalize(card.cardNumber).startsWith(
          normalize(cardNumber.split("/")[0]),
        )),
  );
  const card = exact ?? candidates[0];
  if (!card) throw new Error("PokeTrace card was not found");

  const detail = await request<{ data?: PokeTraceCardDetail }>(
    `/cards/${encodeURIComponent(card.id)}`,
    signal,
  );
  const tiers = detail.data?.gradedOptions?.filter(Boolean) ?? [];
  if (tiers.length === 0) throw new Error("Graded price tiers were not found");
  return { cardId: card.id, tiers };
}

/** PokeTrace를 브라우저에서 직접 호출해 한 감정 등급의 90일 시세를 가져온다. */
export async function getGradePriceHistory(
  cardId: string,
  tier: string,
  signal?: AbortSignal,
): Promise<GradePriceSeries> {
  const result = await request<{ data?: PokeTraceHistoryRow[] }>(
    `/cards/${encodeURIComponent(cardId)}/prices/${encodeURIComponent(tier)}/history?period=90d&limit=90`,
    signal,
  );
  const points = (result.data ?? [])
    .map((row) => ({ date: row.date, price: row.median7d ?? row.avg }))
    .filter((point): point is { date: string; price: number } =>
      Number.isFinite(point.price),
    )
    .sort((a, b) => a.date.localeCompare(b.date));
  if (points.length < 2) throw new Error("Price history is empty");
  return { tier, points };
}
