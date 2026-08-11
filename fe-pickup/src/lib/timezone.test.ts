import { describe, expect, it } from "vitest";
import { kstLocalInputToUtcIso } from "@/lib/timezone";

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
