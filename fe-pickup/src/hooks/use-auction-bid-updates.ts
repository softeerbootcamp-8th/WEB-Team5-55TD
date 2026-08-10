import { useEffect, useRef } from "react";
import { Client, ReconnectionTimeMode, type IMessage } from "@stomp/stompjs";

const HEARTBEAT_INTERVAL_MILLIS = 10_000;
const MAX_RECONNECT_DELAY_MILLIS = 30_000;
const PROCESSED_EVENT_LIMIT = 100;
const AUCTION_STATUSES = new Set(["SCHEDULED", "ONGOING", "WON", "PASSED"]);

type ApiAuctionStatus = "SCHEDULED" | "ONGOING" | "WON" | "PASSED";

export interface AuctionBidUpdatedMessage {
  eventId: string;
  type: "AUCTION_BID_UPDATED";
  auctionId: number;
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

interface UseAuctionBidUpdatesOptions {
  auctionId: string;
  latestBidId?: number;
  onBidUpdated: (message: AuctionBidUpdatedMessage) => void;
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
    value.type === "AUCTION_BID_UPDATED" &&
    typeof value.auctionId === "number" &&
    value.auctionId > 0 &&
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

export function useAuctionBidUpdates({
  auctionId,
  latestBidId,
  onBidUpdated,
  onSubscribed,
}: UseAuctionBidUpdatesOptions) {
  const latestBidIdRef = useRef(latestBidId);
  const onBidUpdatedRef = useRef(onBidUpdated);
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
    onSubscribedRef.current = onSubscribed;
  }, [onBidUpdated, onSubscribed]);

  useEffect(() => {
    const processedEventIds = new Set<string>();
    const processedEventOrder: string[] = [];
    const client = new Client({
      brokerURL: resolveBrokerUrl(),
      connectionTimeout: 10_000,
      heartbeatIncoming: HEARTBEAT_INTERVAL_MILLIS,
      heartbeatOutgoing: HEARTBEAT_INTERVAL_MILLIS,
      reconnectDelay: 1_000 + Math.floor(Math.random() * 1_000),
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: MAX_RECONNECT_DELAY_MILLIS,
    });

    client.onConnect = () => {
      client.subscribe(
        `/topic/auctions/${auctionId}`,
        (frame) => {
          const message = parseMessage(frame);
          if (!message || String(message.auctionId) !== auctionId) {
            console.warn("유효하지 않은 경매 WebSocket 메시지를 수신했습니다.");
            return;
          }
          if (processedEventIds.has(message.eventId)) {
            return;
          }

          processedEventIds.add(message.eventId);
          processedEventOrder.push(message.eventId);
          if (processedEventOrder.length > PROCESSED_EVENT_LIMIT) {
            const expiredEventId = processedEventOrder.shift();
            if (expiredEventId) {
              processedEventIds.delete(expiredEventId);
            }
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

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [auctionId]);
}
