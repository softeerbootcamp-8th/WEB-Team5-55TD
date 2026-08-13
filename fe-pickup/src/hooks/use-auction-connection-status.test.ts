import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

let connect: (() => void) | undefined;
let closeSocket: (() => void) | undefined;
let activateCount = 0;

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    onConnect: (() => void) | undefined;
    onStompError: unknown;
    onWebSocketError: unknown;
    onWebSocketClose: (() => void) | undefined;
    activate() {
      activateCount += 1;
      connect = () => this.onConnect?.();
      closeSocket = () => this.onWebSocketClose?.();
    }
    deactivate() {
      return Promise.resolve();
    }
    subscribe() {
      return { unsubscribe: () => {} };
    }
  },
}));

async function renderConnection(enabled = true) {
  const { useAuctionBidUpdates } =
    await import("@/hooks/use-auction-bid-updates");
  return renderHook(() =>
    useAuctionBidUpdates({
      auctionId: "7",
      enabled,
      onBidUpdated: vi.fn(),
      onSubscribed: vi.fn(),
    }),
  );
}

/** 소켓이 끊긴 뒤 재연결 타이머가 실제로 한 번 돌 때까지 진행시킨다. */
async function failOnce() {
  await act(async () => {
    closeSocket?.();
    await vi.advanceTimersByTimeAsync(31_000);
  });
}

describe("웹소켓 연결 상태", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    connect = undefined;
    closeSocket = undefined;
    activateCount = 0;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("구독이 끝나기 전에는 재연결 중이다", async () => {
    const { result } = await renderConnection();

    expect(result.current).toBe("reconnecting");
  });

  it("연결되고 구독까지 끝나면 실시간이다", async () => {
    const { result } = await renderConnection();

    await act(async () => connect?.());

    expect(result.current).toBe("connected");
  });

  it("연결이 끊기면 재연결 중으로 돌아간다", async () => {
    const { result } = await renderConnection();
    await act(async () => connect?.());

    await failOnce();

    expect(result.current).toBe("reconnecting");
  });

  it("재연결을 5번까지 실패하는 동안은 재연결 중을 유지한다", async () => {
    const { result } = await renderConnection();
    await act(async () => connect?.());

    for (let attempt = 0; attempt < 5; attempt += 1) {
      await failOnce();
    }

    expect(result.current).toBe("reconnecting");
  });

  it("재연결을 5번 넘게 실패하면 연결 끊김이다", async () => {
    const { result } = await renderConnection();
    await act(async () => connect?.());

    for (let attempt = 0; attempt < 6; attempt += 1) {
      await failOnce();
    }

    expect(result.current).toBe("disconnected");
  });

  it("끊김 상태에서 다시 연결되면 실시간으로 복구된다", async () => {
    const { result } = await renderConnection();
    await act(async () => connect?.());
    for (let attempt = 0; attempt < 6; attempt += 1) {
      await failOnce();
    }

    await act(async () => connect?.());

    expect(result.current).toBe("connected");
  });

  it("enabled가 false면 연결을 시도하지 않는다", async () => {
    const { result } = await renderConnection(false);

    expect(activateCount).toBe(0);
    expect(result.current).toBe("reconnecting");
  });
});
