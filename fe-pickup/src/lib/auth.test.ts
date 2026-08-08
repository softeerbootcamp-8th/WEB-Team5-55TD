import { beforeEach, describe, expect, it } from "vitest";
import { setAuthenticated, setNickname } from "@/lib/auth";
import { act, renderHook } from "@testing-library/react";
import { useIsAuthenticated, useNickname } from "@/lib/auth";

describe("authentication storage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("stores authentication and nickname", () => {
    setAuthenticated(true);
    setNickname("테스터");
    expect(localStorage.getItem("pickup:isAuthenticated")).toBe("true");
    expect(localStorage.getItem("pickup:nickname")).toBe("테스터");
  });

  it("clears authentication and nickname on logout", () => {
    setAuthenticated(true);
    setNickname("테스터");
    setAuthenticated(false);
    expect(localStorage.getItem("pickup:isAuthenticated")).toBeNull();
    expect(localStorage.getItem("pickup:nickname")).toBeNull();
  });

  it("notifies subscribed hooks when session state changes", () => {
    const auth = renderHook(() => useIsAuthenticated());
    const nickname = renderHook(() => useNickname());

    act(() => {
      setAuthenticated(true);
      setNickname("테스터");
    });

    expect(auth.result.current).toBe(true);
    expect(nickname.result.current).toBe("테스터");

    auth.unmount();
    nickname.unmount();
  });
});
