import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

type Subscriber = (frame: { body: string }) => void;

const subscriptions = new Map<string, Subscriber>();
let connect: (() => void) | undefined;

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    onConnect: (() => void) | undefined;
    onStompError: unknown;
    onWebSocketError: unknown;
    onWebSocketClose: unknown;
    activate() {
      connect = () => this.onConnect?.();
    }
    deactivate() {
      return Promise.resolve();
    }
    subscribe(destination: string, subscriber: Subscriber) {
      subscriptions.set(destination, subscriber);
      return { unsubscribe: () => subscriptions.delete(destination) };
    }
  },
}));

function failureFrame(overrides: Record<string, unknown> = {}) {
  return {
    body: JSON.stringify({
      eventId: "event-1",
      type: "BID_REQUEST_FAILED",
      auctionId: 7,
      bidRequestId: 55,
      bidPrice: 20_000,
      failureCode: "INSUFFICIENT_BID_LIMIT",
      failureMessage: "보유 포인트가 입찰 금액보다 적습니다.",
      occurredAt: "2026-08-12T00:00:00Z",
      ...overrides,
    }),
  };
}

describe("입찰 요청 실패 알림 구독", () => {
  beforeEach(() => {
    subscriptions.clear();
    connect = undefined;
  });

  it("본인 큐로 도착한 실패 사유를 그대로 전달한다", async () => {
    const { useAuctionBidUpdates } = await import(
      "@/hooks/use-auction-bid-updates"
    );
    const onBidFailed = vi.fn();
    renderHook(() =>
      useAuctionBidUpdates({
        auctionId: "7",
        onBidUpdated: vi.fn(),
        onBidFailed,
        onSubscribed: vi.fn(),
      }),
    );
    connect?.();

    subscriptions.get("/user/queue/bid-requests")?.(failureFrame());

    expect(onBidFailed).toHaveBeenCalledWith(
      expect.objectContaining({
        bidRequestId: 55,
        failureCode: "INSUFFICIENT_BID_LIMIT",
        failureMessage: "보유 포인트가 입찰 금액보다 적습니다.",
      }),
    );
  });

  it("다른 경매의 실패나 중복 이벤트는 무시한다", async () => {
    const { useAuctionBidUpdates } = await import(
      "@/hooks/use-auction-bid-updates"
    );
    const onBidFailed = vi.fn();
    renderHook(() =>
      useAuctionBidUpdates({
        auctionId: "7",
        onBidUpdated: vi.fn(),
        onBidFailed,
        onSubscribed: vi.fn(),
      }),
    );
    connect?.();
    const subscriber = subscriptions.get("/user/queue/bid-requests");

    subscriber?.(failureFrame({ auctionId: 9 }));
    expect(onBidFailed).not.toHaveBeenCalled();

    subscriber?.(failureFrame());
    subscriber?.(failureFrame());
    expect(onBidFailed).toHaveBeenCalledTimes(1);

    subscriber?.({ body: "not-json" });
    expect(onBidFailed).toHaveBeenCalledTimes(1);
  });
});
