import { useEffect, useRef } from "react";
import { Client, type IMessage } from "@stomp/stompjs";

const HEARTBEAT_INTERVAL_MILLIS = 10_000;
const INITIAL_RECONNECT_DELAY_MILLIS = 1_000;
const MAX_RECONNECT_DELAY_MILLIS = 30_000;
const RECONNECT_JITTER_MILLIS = 1_000;
const PROCESSED_EVENT_LIMIT = 100;
const BID_REQUESTS_USER_QUEUE = "/user/queue/bid-requests";
const AUCTION_STATUSES = new Set(["SCHEDULED", "ONGOING", "WON", "PASSED"]);

export function calculateReconnectDelay(
  attempt: number,
  randomValue = Math.random(),
) {
  const baseDelay = Math.min(
    INITIAL_RECONNECT_DELAY_MILLIS * 2 ** Math.max(attempt - 1, 0),
    MAX_RECONNECT_DELAY_MILLIS,
  );
  const jitter = Math.floor(
    Math.min(Math.max(randomValue, 0), 0.999999) * RECONNECT_JITTER_MILLIS,
  );
  return Math.min(baseDelay + jitter, MAX_RECONNECT_DELAY_MILLIS);
}

type ApiAuctionStatus = "SCHEDULED" | "ONGOING" | "WON" | "PASSED";

export interface AuctionBidUpdatedMessage {
  eventId: string;
  type: "BID_REQUEST_SUCCEEDED";
  auctionId: number;
  bidRequestId: number | null;
  auctionStatus: ApiAuctionStatus;
  currentPrice: number;
  startedAt: string;
  endedAt: string | null;
  latestBid: {
    bidId: number;
    nicknameMasked: string;
    bidPrice: number;
    createdAt: string;
  };
  occurredAt: string;
}

export interface BidRequestFailedMessage {
  eventId: string;
  type: "BID_REQUEST_FAILED";
  auctionId: number;
  bidRequestId: number;
  bidPrice: number;
  failureCode: string;
  failureMessage: string;
  occurredAt: string;
}

interface UseAuctionBidUpdatesOptions {
  auctionId: string;
  latestBidId?: number;
  onBidUpdated: (message: AuctionBidUpdatedMessage) => void;
  onBidRequestFailed?: (message: BidRequestFailedMessage) => void;
  onSubscribed: () => void;
}

function resolveBrokerUrl() {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "/api";
  const baseUrl = new URL(apiBaseUrl, window.location.origin);
  const basePath = baseUrl.pathname.replace(/\/$/, "");

  baseUrl.pathname = `${basePath}/ws`;
  baseUrl.protocol = baseUrl.protocol === "https:" ? "wss:" : "ws:";
  return baseUrl.toString();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseMessage(frame: IMessage): AuctionBidUpdatedMessage | null {
  let value: unknown;
  try {
    value = JSON.parse(frame.body);
  } catch {
    return null;
  }

  if (!isRecord(value) || !isRecord(value.latestBid)) {
    return null;
  }

  const latestBid = value.latestBid;
  const isValid =
    typeof value.eventId === "string" &&
    value.eventId.length > 0 &&
    value.type === "BID_REQUEST_SUCCEEDED" &&
    typeof value.auctionId === "number" &&
    value.auctionId > 0 &&
    (value.bidRequestId === null || typeof value.bidRequestId === "number") &&
    typeof value.auctionStatus === "string" &&
    AUCTION_STATUSES.has(value.auctionStatus) &&
    typeof value.currentPrice === "number" &&
    Number.isFinite(value.currentPrice) &&
    typeof value.startedAt === "string" &&
    (value.endedAt === null || typeof value.endedAt === "string") &&
    typeof value.occurredAt === "string" &&
    typeof latestBid.bidId === "number" &&
    latestBid.bidId > 0 &&
    typeof latestBid.nicknameMasked === "string" &&
    typeof latestBid.bidPrice === "number" &&
    Number.isFinite(latestBid.bidPrice) &&
    typeof latestBid.createdAt === "string";

  return isValid ? (value as unknown as AuctionBidUpdatedMessage) : null;
}

function parseBidRequestFailedMessage(frame: IMessage): BidRequestFailedMessage | null {
  let value: unknown;
  try {
    value = JSON.parse(frame.body);
  } catch {
    return null;
  }

  if (!isRecord(value)) {
    return null;
  }

  const isValid =
    typeof value.eventId === "string" &&
    value.eventId.length > 0 &&
    value.type === "BID_REQUEST_FAILED" &&
    typeof value.auctionId === "number" &&
    value.auctionId > 0 &&
    typeof value.bidRequestId === "number" &&
    typeof value.bidPrice === "number" &&
    Number.isFinite(value.bidPrice) &&
    typeof value.failureCode === "string" &&
    typeof value.failureMessage === "string" &&
    typeof value.occurredAt === "string";

  return isValid ? (value as unknown as BidRequestFailedMessage) : null;
}

export function useAuctionBidUpdates({
  auctionId,
  latestBidId,
  onBidUpdated,
  onBidRequestFailed,
  onSubscribed,
}: UseAuctionBidUpdatesOptions) {
  const latestBidIdRef = useRef(latestBidId);
  const onBidUpdatedRef = useRef(onBidUpdated);
  const onBidRequestFailedRef = useRef(onBidRequestFailed);
  const onSubscribedRef = useRef(onSubscribed);

  useEffect(() => {
    latestBidIdRef.current = undefined;
  }, [auctionId]);

  useEffect(() => {
    if (
      latestBidId !== undefined &&
      (latestBidIdRef.current === undefined ||
        latestBidId > latestBidIdRef.current)
    ) {
      latestBidIdRef.current = latestBidId;
    }
  }, [latestBidId]);

  useEffect(() => {
    onBidUpdatedRef.current = onBidUpdated;
    onBidRequestFailedRef.current = onBidRequestFailed;
    onSubscribedRef.current = onSubscribed;
  }, [onBidUpdated, onBidRequestFailed, onSubscribed]);

  useEffect(() => {
    const processedEventIds = new Set<string>();
    const processedEventOrder: string[] = [];
    let reconnectAttempt = 0;
    let reconnectTimer: number | undefined;
    let isReconnecting = false;
    let isDisposed = false;

    function markProcessed(eventId: string): boolean {
      if (processedEventIds.has(eventId)) {
        return false;
      }
      processedEventIds.add(eventId);
      processedEventOrder.push(eventId);
      if (processedEventOrder.length > PROCESSED_EVENT_LIMIT) {
        const expiredEventId = processedEventOrder.shift();
        if (expiredEventId) {
          processedEventIds.delete(expiredEventId);
        }
      }
      return true;
    }

    const client = new Client({
      brokerURL: resolveBrokerUrl(),
      connectionTimeout: 10_000,
      heartbeatIncoming: HEARTBEAT_INTERVAL_MILLIS,
      heartbeatOutgoing: HEARTBEAT_INTERVAL_MILLIS,
      reconnectDelay: 0,
    });

    const scheduleReconnect = () => {
      if (isDisposed || reconnectTimer !== undefined || isReconnecting) return;

      reconnectAttempt += 1;
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = undefined;
        if (isDisposed) return;
        isReconnecting = true;

        void client.deactivate({ force: true }).then(
          () => {
            isReconnecting = false;
            if (!isDisposed) client.activate();
          },
          () => {
            isReconnecting = false;
            if (!isDisposed) client.activate();
          },
        );
      }, calculateReconnectDelay(reconnectAttempt));
    };

    client.onConnect = () => {
      reconnectAttempt = 0;
      client.subscribe(
        `/topic/auctions/${auctionId}`,
        (frame) => {
          const message = parseMessage(frame);
          if (!message || String(message.auctionId) !== auctionId) {
            console.warn("유효하지 않은 경매 WebSocket 메시지를 수신했습니다.");
            return;
          }
          if (!markProcessed(message.eventId)) {
            return;
          }

          if (
            latestBidIdRef.current !== undefined &&
            message.latestBid.bidId <= latestBidIdRef.current
          ) {
            return;
          }

          latestBidIdRef.current = message.latestBid.bidId;
          onBidUpdatedRef.current(message);
        },
      );
      // 로그인하지 않은 사용자는 핸드셰이크에서 Principal이 없어 이 구독으로 오는 메시지가 없다.
      client.subscribe(BID_REQUESTS_USER_QUEUE, (frame) => {
        const message = parseBidRequestFailedMessage(frame);
        if (!message) {
          console.warn("유효하지 않은 입찰 요청 실패 메시지를 수신했습니다.");
          return;
        }
        if (!markProcessed(message.eventId)) {
          return;
        }
        onBidRequestFailedRef.current?.(message);
      });
      onSubscribedRef.current();
    };
    client.onStompError = (frame) => {
      console.warn(
        "경매 STOMP 연결에서 오류가 발생했습니다.",
        frame.headers.message,
      );
    };
    client.onWebSocketError = () => {
      console.warn(
        "경매 WebSocket 연결에 실패했습니다. REST 조회로 복구를 계속합니다.",
      );
    };
    client.onWebSocketClose = () => {
      scheduleReconnect();
    };

    client.activate();
    return () => {
      isDisposed = true;
      if (reconnectTimer !== undefined) {
        window.clearTimeout(reconnectTimer);
      }
      void client.deactivate();
    };
  }, [auctionId]);
}
