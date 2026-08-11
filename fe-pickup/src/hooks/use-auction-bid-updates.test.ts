import { describe, expect, it } from "vitest";
import { calculateReconnectDelay } from "@/hooks/use-auction-bid-updates";

describe("calculateReconnectDelay", () => {
  it("재연결 시도마다 지수적으로 증가하는 기본 지연에 jitter를 더한다", () => {
    expect(calculateReconnectDelay(1, 0)).toBe(1_000);
    expect(calculateReconnectDelay(2, 0.999)).toBe(2_999);
    expect(calculateReconnectDelay(3, 0.5)).toBe(4_500);
    expect(calculateReconnectDelay(4, 0)).toBe(8_000);
  });

  it("최대 재연결 지연은 30초를 넘지 않는다", () => {
    expect(calculateReconnectDelay(10, 0.999)).toBe(30_000);
  });
});
