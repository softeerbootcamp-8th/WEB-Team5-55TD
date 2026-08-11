import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MarketPriceChart } from "./market-price-chart";

const findGradedCard = vi.fn();
const getGradePriceHistory = vi.fn();

vi.mock("@/api/poketrace", () => ({
  findGradedCard: (...args: unknown[]) => findGradedCard(...args),
  getGradePriceHistory: (...args: unknown[]) => getGradePriceHistory(...args),
}));

describe("MarketPriceChart", () => {
  beforeEach(() => {
    findGradedCard.mockReset();
    getGradePriceHistory.mockReset();
  });

  it("경매 카드 등급을 우선 선택해 시계열을 표시한다", async () => {
    findGradedCard.mockResolvedValue({
      cardId: "card-1",
      tiers: ["PSA_10", "PSA_9", "BGS_9_5"],
    });
    getGradePriceHistory.mockResolvedValue({
      tier: "PSA_9",
      points: [
        { date: "2026-01-01", price: 100 },
        { date: "2026-02-01", price: 120 },
      ],
    });
    render(
      <MarketPriceChart
        cardName="Charizard"
        setName="Base Set"
        cardNumber="4/102"
        preferredAgency="PSA"
        preferredScore="9"
      />,
    );
    expect(
      await screen.findByRole("heading", { name: "등급별 시세" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "PSA 9" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      screen.getByRole("img", { name: /PSA 9 최근 90일/ }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("$120")).toHaveLength(2);
  });

  it("카드 식별이나 시계열 조회가 실패하면 컴포넌트를 노출하지 않는다", async () => {
    findGradedCard.mockRejectedValue(new Error("network"));
    const { container } = render(<MarketPriceChart cardName="Unknown" />);
    await waitFor(() => expect(findGradedCard).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
