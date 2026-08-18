import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

interface MockClient {
  config: Record<string, unknown>;
  onConnect?: () => void;
  onStompError?: (frame: { headers: { message: string } }) => void;
  onWebSocketError?: () => void;
  onWebSocketClose?: () => void;
  onHeartbeatLost?: () => void;
  deactivate: ReturnType<typeof vi.fn>;
}

let clients: MockClient[] = [];
let isOnline = true;
let visibilityState: DocumentVisibilityState = "visible";
let activateCount = 0;

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    config: Record<string, unknown>;
    onConnect: (() => void) | undefined;
    onStompError:
      ((frame: { headers: { message: string } }) => void) | undefined;
    onWebSocketError: (() => void) | undefined;
    onWebSocketClose: (() => void) | undefined;
    onHeartbeatLost: (() => void) | undefined;
    deactivate = vi.fn(() => Promise.resolve());

    constructor(config: Record<string, unknown>) {
      this.config = config;
      clients.push(this);
    }

    activate() {
      activateCount += 1;
    }

    subscribe() {
      return { unsubscribe: () => {} };
    }
  },
}));

function currentClient() {
  const client = clients.at(-1);
  if (!client) throw new Error("활성화된 STOMP 클라이언트가 없습니다.");
  return client;
}

async function connect() {
  await act(async () => currentClient().onConnect?.());
}

async function closeSocket() {
  await act(async () => currentClient().onWebSocketClose?.());
}

async function setBrowserOnline(nextIsOnline: boolean) {
  isOnline = nextIsOnline;
  await act(async () => {
    window.dispatchEvent(new Event(nextIsOnline ? "online" : "offline"));
  });
}

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
  await closeSocket();
  await act(async () => vi.advanceTimersByTimeAsync(31_000));
}

describe("웹소켓 연결 상태", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    clients = [];
    isOnline = true;
    visibilityState = "visible";
    activateCount = 0;
    vi.spyOn(window.navigator, "onLine", "get").mockImplementation(
      () => isOnline,
    );
    vi.spyOn(document, "visibilityState", "get").mockImplementation(
      () => visibilityState,
    );
    vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("구독이 끝나기 전에는 재연결 중이다", async () => {
    const { result } = await renderConnection();

    expect(result.current).toBe("reconnecting");
  });

  it("연결되고 구독까지 끝나면 실시간이다", async () => {
    const { result } = await renderConnection();

    await connect();

    expect(result.current).toBe("connected");
  });

  it("다른 경매로 이동하면 새 구독 전까지 재연결 중이다", async () => {
    const { useAuctionBidUpdates } =
      await import("@/hooks/use-auction-bid-updates");
    const { result, rerender } = renderHook(
      ({ auctionId }) =>
        useAuctionBidUpdates({
          auctionId,
          onBidUpdated: vi.fn(),
          onSubscribed: vi.fn(),
        }),
      { initialProps: { auctionId: "7" } },
    );
    await connect();
    const previousClient = currentClient();

    rerender({ auctionId: "8" });

    expect(previousClient.deactivate).toHaveBeenCalledOnce();
    expect(activateCount).toBe(2);
    expect(result.current).toBe("reconnecting");

    await connect();
    expect(result.current).toBe("connected");
  });

  it("처음부터 오프라인이면 연결하지 않고 연결 끊김이다", async () => {
    isOnline = false;

    const { result } = await renderConnection();

    expect(activateCount).toBe(0);
    expect(result.current).toBe("disconnected");
  });

  it("연결 중 오프라인이 되면 즉시 연결 끊김이다", async () => {
    const { result } = await renderConnection();
    await connect();

    await setBrowserOnline(false);

    expect(result.current).toBe("disconnected");
    expect(currentClient().deactivate).toHaveBeenCalledWith({ force: true });
    await act(async () => vi.advanceTimersByTimeAsync(31_000));
    expect(activateCount).toBe(1);
  });

  it("온라인으로 돌아오면 재연결 후 실시간으로 복구된다", async () => {
    const { result } = await renderConnection();
    await connect();
    await setBrowserOnline(false);

    await setBrowserOnline(true);

    expect(result.current).toBe("reconnecting");
    expect(activateCount).toBe(2);

    await connect();
    expect(result.current).toBe("connected");
  });

  it("연결이 끊기면 재연결 중으로 돌아간다", async () => {
    const { result } = await renderConnection();
    await connect();

    await failOnce();

    expect(result.current).toBe("reconnecting");
  });

  it("WebSocket 오류가 나면 기존 소켓을 폐기하고 재연결한다", async () => {
    const { result } = await renderConnection();
    await connect();
    const failedClient = currentClient();

    await act(async () => failedClient.onWebSocketError?.());

    expect(result.current).toBe("reconnecting");
    expect(failedClient.deactivate).toHaveBeenCalledWith({ force: true });
    await act(async () => vi.advanceTimersByTimeAsync(31_000));
    expect(activateCount).toBe(2);
  });

  it("STOMP 오류가 나면 기존 소켓을 폐기하고 재연결한다", async () => {
    const { result } = await renderConnection();
    await connect();
    const failedClient = currentClient();

    await act(async () =>
      failedClient.onStompError?.({ headers: { message: "broker error" } }),
    );

    expect(result.current).toBe("reconnecting");
    expect(failedClient.deactivate).toHaveBeenCalledWith({ force: true });
    await act(async () => vi.advanceTimersByTimeAsync(31_000));
    expect(activateCount).toBe(2);
  });

  it("하트비트를 잃으면 기존 소켓을 폐기하고 재연결한다", async () => {
    const { result } = await renderConnection();
    await connect();
    const failedClient = currentClient();

    await act(async () => failedClient.onHeartbeatLost?.());

    expect(result.current).toBe("reconnecting");
    expect(failedClient.config.discardWebsocketOnCommFailure).toBe(true);
    await act(async () => vi.advanceTimersByTimeAsync(31_000));
    expect(activateCount).toBe(2);
  });

  it("오프라인 이벤트가 누락돼도 재시도 전에 네트워크를 다시 확인한다", async () => {
    const { result } = await renderConnection();
    await connect();
    await closeSocket();

    isOnline = false;
    await act(async () => vi.advanceTimersByTimeAsync(31_000));

    expect(result.current).toBe("disconnected");
    expect(activateCount).toBe(1);
  });

  it("중복 오류와 종료 이벤트는 재연결을 한 번만 예약한다", async () => {
    await renderConnection();
    await connect();
    const failedClient = currentClient();

    await act(async () => {
      failedClient.onWebSocketError?.();
      failedClient.onWebSocketClose?.();
      failedClient.onHeartbeatLost?.();
    });
    await act(async () => vi.advanceTimersByTimeAsync(31_000));

    expect(activateCount).toBe(2);
  });

  it("모바일 화면으로 복귀하면 소켓을 새로 연결한다", async () => {
    const { result } = await renderConnection();
    await connect();
    const staleClient = currentClient();

    visibilityState = "hidden";
    await act(async () =>
      document.dispatchEvent(new Event("visibilitychange")),
    );
    visibilityState = "visible";
    await act(async () =>
      document.dispatchEvent(new Event("visibilitychange")),
    );

    expect(result.current).toBe("reconnecting");
    expect(staleClient.deactivate).toHaveBeenCalledWith({ force: true });
    expect(activateCount).toBe(2);
  });

  it("bfcache에서 복원되면 소켓을 새로 연결한다", async () => {
    const { result } = await renderConnection();
    await connect();
    const staleClient = currentClient();
    const pageShowEvent = new Event("pageshow");
    Object.defineProperty(pageShowEvent, "persisted", { value: true });

    await act(async () => window.dispatchEvent(pageShowEvent));

    expect(result.current).toBe("reconnecting");
    expect(staleClient.deactivate).toHaveBeenCalledWith({ force: true });
    expect(activateCount).toBe(2);
  });

  it("재연결을 5번까지 실패하는 동안은 재연결 중을 유지한다", async () => {
    const { result } = await renderConnection();
    await connect();

    for (let attempt = 0; attempt < 5; attempt += 1) {
      await failOnce();
    }

    expect(result.current).toBe("reconnecting");
  });

  it("재연결을 5번 넘게 실패하면 연결 끊김이다", async () => {
    const { result } = await renderConnection();
    await connect();

    for (let attempt = 0; attempt < 6; attempt += 1) {
      await failOnce();
    }

    expect(result.current).toBe("disconnected");
  });

  it("끊김 상태에서 다시 연결되면 실시간으로 복구된다", async () => {
    const { result } = await renderConnection();
    await connect();
    for (let attempt = 0; attempt < 6; attempt += 1) {
      await failOnce();
    }

    await connect();

    expect(result.current).toBe("connected");
  });

  it("언마운트하면 예약된 재연결과 늦은 콜백을 무시한다", async () => {
    const { result, unmount } = await renderConnection();
    await connect();
    const disposedClient = currentClient();
    await closeSocket();

    unmount();
    await act(async () => {
      disposedClient.onConnect?.();
      await vi.advanceTimersByTimeAsync(31_000);
    });

    expect(activateCount).toBe(1);
    expect(result.current).toBe("reconnecting");
  });

  it("통신 장애 소켓은 정상 close를 기다리지 않고 폐기한다", async () => {
    await renderConnection();

    expect(currentClient().config.discardWebsocketOnCommFailure).toBe(true);
  });

  it("enabled가 false면 연결을 시도하지 않는다", async () => {
    const { result } = await renderConnection(false);

    expect(activateCount).toBe(0);
    expect(result.current).toBe("reconnecting");
  });
});
