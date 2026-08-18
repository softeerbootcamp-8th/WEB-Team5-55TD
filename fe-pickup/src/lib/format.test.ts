import {
  caretPositionAfterDigits,
  formatAmountInput,
  formatWon,
  formatWonCompact,
  formatPoint,
  formatCountdown,
  formatDate,
  formatDateTime,
  relativeTime,
  minBidUnit,
} from "@/lib/format";
import { describe, expect, it } from "vitest";

describe("format utilities", () => {
  it("formats optional money and points values", () => {
    expect(formatWon()).toBe("-");
    expect(formatWon(1280000)).toBe("1,280,000원");
    expect(formatPoint()).toBe("-");
    expect(formatPoint(1280000)).toBe("1,280,000P");
  });

  it("keeps amounts under 100만원 exact, abbreviates above into 억/만 units", () => {
    expect(formatWonCompact()).toBe("-");
    expect(formatWonCompact(3_400)).toBe("3,400원");
    expect(formatWonCompact(340_000)).toBe("340,000원");
    expect(formatWonCompact(999_999)).toBe("999,999원");
    expect(formatWonCompact(1_280_000)).toBe("128만원");
    expect(formatWonCompact(9_600_000)).toBe("960만원");
    expect(formatWonCompact(100_000_000)).toBe("1억원");
    expect(formatWonCompact(123_450_000)).toBe("1억 2,345만원");
    expect(formatWonCompact(-123_450_000)).toBe("-1억 2,345만원");
  });

  it("clamps and pads countdown values", () => {
    expect(formatCountdown(-1)).toBe("00 : 00 : 00 : 00");
    expect(formatCountdown(3_661_000)).toBe("00 : 01 : 01 : 01");
    expect(formatCountdown(90_061_000)).toBe("01 : 01 : 01 : 01");
  });

  it("formats dates in KST regardless of the runner's local timezone", () => {
    expect(formatDateTime()).toBe("-");
    // 서버는 UTC(Z 접미사)로 내려주고, 화면에는 KST(UTC+9)로 보여준다.
    expect(formatDateTime("2026-07-22T06:04:00Z")).toBe("2026.07.22 15:04");
    // KST로 변환하면 다음 날로 넘어가는 경계도 올바르게 처리한다.
    expect(formatDateTime("2026-07-22T15:30:00Z")).toBe("2026.07.23 00:30");
  });

  it("formats date-only values without a bogus time, in any timezone", () => {
    expect(formatDate()).toBe("-");
    // 검수 완료일은 시간 없는 LocalDate 로 내려온다. 시각이 붙으면 안 된다.
    expect(formatDate("2026-07-20")).toBe("2026.07.20");

    const originalTimeZone = process.env.TZ;
    for (const timeZone of ["America/New_York", "UTC", "Asia/Seoul"]) {
      process.env.TZ = timeZone;
      expect(formatDate("2026-07-20")).toBe("2026.07.20");
    }
    process.env.TZ = originalTimeZone;
  });

  it("입력 중인 금액에 3자리 콤마를 다시 매긴다", () => {
    expect(formatAmountInput("")).toBe("");
    expect(formatAmountInput("1234567")).toBe("1,234,567");
    // 아무 자리에나 찍힌 콤마도 제자리로 돌아온다.
    expect(formatAmountInput("1,2,3,4,5,6,7")).toBe("1,234,567");
    expect(formatAmountInput("12,34")).toBe("1,234");
    // 숫자가 아닌 문자는 버린다.
    expect(formatAmountInput("1a2b3원")).toBe("123");
    expect(formatAmountInput("abc")).toBe("");
    expect(formatAmountInput("000")).toBe("0");
    expect(formatAmountInput("007")).toBe("7");
    // 자릿수가 커도 값이 뭉개지지 않는다.
    expect(formatAmountInput("9007199254740993")).toBe("9,007,199,254,740,993");
  });

  it("콤마가 끼어도 커서가 같은 숫자 뒤에 남는다", () => {
    expect(caretPositionAfterDigits("1,234", 0)).toBe(0);
    expect(caretPositionAfterDigits("1,234", 1)).toBe(1);
    // "12" 까지 입력한 뒤라면 콤마 다음 자리에 커서가 있어야 한다.
    expect(caretPositionAfterDigits("1,234", 2)).toBe(3);
    expect(caretPositionAfterDigits("1,234", 4)).toBe(5);
    expect(caretPositionAfterDigits("1,234", 9)).toBe(5);
  });

  it("formats relative times at each boundary", () => {
    const now = Date.parse("2026-07-22T15:00:00Z");
    expect(relativeTime(new Date(now - 9_000).toISOString(), now)).toBe(
      "방금 전",
    );
    expect(relativeTime(new Date(now - 30_000).toISOString(), now)).toBe(
      "30초 전",
    );
    expect(relativeTime(new Date(now - 120_000).toISOString(), now)).toBe(
      "2분 전",
    );
    expect(relativeTime(new Date(now - 7_200_000).toISOString(), now)).toBe(
      "2시간 전",
    );
    expect(relativeTime(new Date(now - 172_800_000).toISOString(), now)).toBe(
      "2일 전",
    );
  });

  it("rounds the minimum bid unit the same way the server stores it", () => {
    // 서버: Math.round(startingPrice * 0.05)
    expect(minBidUnit(10_000)).toBe(500);
    expect(minBidUnit(10_001)).toBe(500);
    expect(minBidUnit(12_345)).toBe(617);
  });
});
