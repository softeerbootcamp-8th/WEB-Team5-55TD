import { describe, expect, it } from "vitest";
import { CardState } from "@/api/generated/model";
import { getCardStateLabel } from "@/lib/card-state";

describe("카드 상태", () => {
  it("상태 코드를 상·중·하로 표시한다", () => {
    expect(getCardStateLabel(CardState.HIGH)).toBe("상");
    expect(getCardStateLabel(CardState.MEDIUM)).toBe("중");
    expect(getCardStateLabel(CardState.LOW)).toBe("하");
  });

  it("기존 상품에 상태가 없으면 대시를 표시한다", () => {
    expect(getCardStateLabel()).toBe("-");
  });
});
