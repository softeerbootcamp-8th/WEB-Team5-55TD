import { cn } from "@/lib/utils";
import { describe, expect, it } from "vitest";

describe("cn", () => {
  it("combines conditional classes and resolves Tailwind conflicts", () => {
    expect(cn("px-2", undefined, "text-sm")).toBe("px-2 text-sm");
    expect(cn("p-2", "p-4")).toBe("p-4");
  });
});
