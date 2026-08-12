import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MarketPriceChart } from "./market-price-chart";

describe("MarketPriceChart", () => {
  it("경매 카드 등급을 우선 선택해 시계열을 표시한다", async () => {
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
  });
});
