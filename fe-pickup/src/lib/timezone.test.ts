import { describe, expect, it } from "vitest";
import {
  kstLocalInputToUtcIso,
  todayDateInputValue,
  utcInstantToKstLocalInput,
} from "@/lib/timezone";

describe("kstLocalInputToUtcIso", () => {
  it("converts a KST wall-clock datetime-local value to a UTC ISO string", () => {
    expect(kstLocalInputToUtcIso("2026-08-01T10:00")).toBe(
      "2026-08-01T01:00:00.000Z",
    );
  });

  it("rolls over to the previous day when KST time is before 09:00", () => {
    expect(kstLocalInputToUtcIso("2026-08-01T03:15")).toBe(
      "2026-07-31T18:15:00.000Z",
    );
  });
});

describe("utcInstantToKstLocalInput", () => {
  it("converts a UTC instant to a KST datetime-local value", () => {
    expect(utcInstantToKstLocalInput("2026-08-01T01:00:00Z")).toBe(
      "2026-08-01T10:00",
    );
  });

  it("rolls over to the next day in KST", () => {
    expect(utcInstantToKstLocalInput("2026-08-01T15:30:00Z")).toBe(
      "2026-08-02T00:30",
    );
  });
});

describe("todayDateInputValue", () => {
  it("returns today's KST date as YYYY-MM-DD", () => {
    const expected = new Date(Date.now() + 9 * 60 * 60 * 1000)
      .toISOString()
      .slice(0, 10);
    expect(todayDateInputValue()).toBe(expected);
  });
});
