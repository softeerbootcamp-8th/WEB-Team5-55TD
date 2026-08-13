import { beforeEach, describe, expect, it, vi } from "vitest";
import { findGradedCard, getGradePriceHistory } from "./poketrace";

const fetchMock = vi.fn();

describe("PokeTrace API", () => {
  beforeEach(() => {
    vi.stubEnv("VITE_POKETRACE_API_KEY", "test-key");
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });

  it("카드를 식별하고 조회 가능한 감정 등급을 반환한다", async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [
            {
              id: "card-1",
              name: "Charizard",
              cardNumber: "4/102",
              set: { name: "Base Set" },
            },
          ],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: { id: "card-1", gradedOptions: ["PSA_10", "PSA_9"] },
        }),
      });

    await expect(
      findGradedCard("Charizard", "Base Set", "4/102"),
    ).resolves.toEqual({ cardId: "card-1", tiers: ["PSA_10", "PSA_9"] });
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("api.poketrace.com/v1/cards?"),
      expect.objectContaining({ headers: { "X-API-Key": "test-key" } }),
    );
  });

  it("7일 중앙값을 우선 사용하고 날짜순으로 시계열을 정렬한다", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: [
          { date: "2026-02-02", avg: 120, median7d: 115 },
          { date: "2026-02-01", avg: 100, median7d: null },
        ],
      }),
    });
    await expect(getGradePriceHistory("card-1", "PSA_10")).resolves.toEqual({
      tier: "PSA_10",
      points: [
        { date: "2026-02-01", price: 100 },
        { date: "2026-02-02", price: 115 },
      ],
    });
  });

  it("응답 오류와 빈 시계열을 실패로 처리한다", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, status: 429 });
    await expect(findGradedCard("Charizard")).rejects.toThrow(
      "PokeTrace request failed",
    );
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: [] }),
    });
    await expect(getGradePriceHistory("card-1", "PSA_10")).rejects.toThrow(
      "Price history is empty",
    );
  });
});
