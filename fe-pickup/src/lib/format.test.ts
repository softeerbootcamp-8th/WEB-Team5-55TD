import {
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
    expect(formatCountdown(-1)).toBe("00 : 00 : 00");
    expect(formatCountdown(3_661_000)).toBe("01 : 01 : 01");
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
