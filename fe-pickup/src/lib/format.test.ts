import {
  formatWon,
  formatPoint,
  formatCountdown,
  formatDateTime,
  relativeTime,
  maskNickname,
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

  it("clamps and pads countdown values", () => {
    expect(formatCountdown(-1)).toBe("00 : 00 : 00");
    expect(formatCountdown(3_661_000)).toBe("01 : 01 : 01");
  });

  it("formats dates and handles missing dates", () => {
    expect(formatDateTime()).toBe("-");
    const date = new Date("2026-07-22T15:04:00");
    expect(formatDateTime(date.toISOString())).toMatch(/2026\.07\.22 \d{2}:04/);
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

  it("masks short and long nicknames", () => {
    expect(maskNickname("abc")).toBe("abc***");
    expect(maskNickname("abcdefg")).toBe("abc***fg");
  });

  it("rounds the minimum bid unit up to 100 won", () => {
    expect(minBidUnit(10_000)).toBe(500);
    expect(minBidUnit(10_001)).toBe(600);
  });
});
