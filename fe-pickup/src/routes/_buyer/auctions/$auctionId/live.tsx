import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
} from "react";
import {
  createFileRoute,
  Link,
  notFound,
  useNavigate,
} from "@tanstack/react-router";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type { InfiniteData } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { toast } from "sonner";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { ConnectionStatus } from "@/components/domain/connection-status";
import { BidList, RealtimeBidList } from "@/components/domain/bid-list";
import { Avatar } from "@/components/domain/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { getAuctionDetail } from "@/api/auctions";
import {
  BID_MODAL_SIZE,
  createBidRequest,
  getAuctionBids,
  getBidErrorMessage,
  getBidRequestResult,
  REALTIME_BID_PAGE_SIZE,
} from "@/api/bids";
import { getGetMyPointBalanceQueryKey } from "@/api/generated/member/member";
import { refreshAccessToken } from "@/api/mutator/custom-instance";
import {
  useAuctionBidUpdates,
  type AuctionBidUpdatedMessage,
  type BidRequestFailedMessage,
} from "@/hooks/use-auction-bid-updates";
import { isAuthenticated, useIsAuthenticated, useNickname } from "@/lib/auth";
import {
  setSkipBidConfirm,
  shouldSkipBidConfirm,
} from "@/lib/bid-confirm-preference";
import {
  caretPositionAfterDigits,
  formatAmountInput,
  formatWon,
} from "@/lib/format";
import { AuctionStatus } from "@/lib/types";
import {
  mergeLatestBid,
  type AuctionBidsSnapshot,
} from "@/lib/auction-live-state";
import { pokemonAvatarForKey } from "@/lib/pokemon-avatars";

const ACTIVE_POLLING_INTERVAL_MILLIS = 15_000;
const POLLING_JITTER_MILLIS = 3_000;
const BID_REQUEST_RESULT_POLL_INTERVAL_MILLIS = 1_000;
const BID_REQUEST_RESULT_TIMEOUT_MILLIS = 60_000;
// 경매 종료 감지 즉시 결과 화면으로 튀지 않도록, 짧게 멈췄다가 전환한다.
const AUCTION_END_TRANSITION_DELAY_MILLIS = 500;

function pollingInterval() {
  return (
    ACTIVE_POLLING_INTERVAL_MILLIS +
    Math.floor(Math.random() * POLLING_JITTER_MILLIS)
  );
}

function laterEndTime(
  current: string | null | undefined,
  next: string | null | undefined,
): string | undefined {
  if (!next) return current ?? undefined;
  if (!current) return next;
  return Date.parse(next) >= Date.parse(current) ? next : current;
}

export const Route = createFileRoute("/_buyer/auctions/$auctionId/live")({
  loader: async ({ params }) => {
    if (isAuthenticated()) {
      // 실시간 입찰 도중 access-token 만료(401 → 재발급 → 원 요청 재시도) 왕복 지연이
      // 끼는 걸 줄이기 위해, 경매 참여 화면 진입 시 한 번 선제로 갱신해 둔다.
      // 실패해도 무시한다 — 기존 access-token이 여전히 유효할 수 있고, 실제로 만료된
      // 경우엔 요청 인터셉터의 리액티브 재발급이 안전망으로 남아 있다.
      void refreshAccessToken().catch(() => {});
    }
    try {
      return { auction: await getAuctionDetail(params.auctionId) };
    } catch (error) {
      if (error instanceof AxiosError && error.response?.status === 404) {
        throw notFound();
      }
      throw error;
    }
  },
  component: LiveAuctionPage,
});

/** DESIGN.md · live-auction.html — 실 입찰 API 연동, §5.9 최근 6건 + 전체 모달 */
function LiveAuctionPage() {
  const { auction: initialAuction } = Route.useLoaderData();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAuthenticated = useIsAuthenticated();
  const myNickname = useNickname();
  const auctionQuery = useQuery({
    queryKey: ["auction-detail", initialAuction.id],
    queryFn: () => getAuctionDetail(initialAuction.id),
    initialData: initialAuction,
    staleTime: 0,
    refetchInterval: (query) =>
      query.state.data?.status === AuctionStatus.LIVE &&
      document.visibilityState !== "hidden"
        ? pollingInterval()
        : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const auction = auctionQuery.data;
  const minUnit = auction.minBidUnit ?? 0;
  // 닉네임을 모르면 막지 않는다 — 최종 차단은 서버가 한다.
  const isMyAuction =
    !!myNickname && myNickname === (auction.sellerNickname ?? null);

  const [realtimeSnapshot, setRealtimeSnapshot] = useState({
    auctionId: auction.id,
    price: auction.currentPrice ?? auction.startPrice ?? 0,
    endsAt: auction.endsAt,
  });
  const [amount, setAmount] = useState("");
  const amountInputRef = useRef<HTMLInputElement>(null);
  // 콤마를 다시 매기면 문자열 길이가 바뀌어 커서가 끝으로 튄다. 다시 놓을 자리를 적어둔다.
  const amountCaretRef = useRef<number | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [dontShowConfirmAgain, setDontShowConfirmAgain] = useState(false);
  const [fail, setFail] = useState<string | null>(null);
  const [allBidsOpen, setAllBidsOpen] = useState(false);
  const [isBidRequestPending, setIsBidRequestPending] = useState(false);

  const snapshotPrice = auction.currentPrice ?? auction.startPrice ?? 0;
  const realtimePrice =
    realtimeSnapshot.auctionId === auction.id
      ? realtimeSnapshot.price
      : snapshotPrice;
  const realtimeEndsAt =
    realtimeSnapshot.auctionId === auction.id
      ? realtimeSnapshot.endsAt
      : auction.endsAt;
  const currentPrice = Math.max(realtimePrice, snapshotPrice);
  const endsAt = laterEndTime(auction.endsAt, realtimeEndsAt);
  const minNext = currentPrice + minUnit;
  const recommendedAmounts = [
    minNext,
    minNext + minUnit,
    minNext + minUnit * 2,
  ];
  const parsedAmount = (() => {
    const normalized = amount.trim().replaceAll(",", "");
    if (!/^\d+$/.test(normalized)) return null;
    const value = Number(normalized);
    return Number.isSafeInteger(value) && value >= minNext ? value : null;
  })();
  // + 버튼 기준값: 최소 입찰가 미만이거나 아직 입력하지 않은 값도 base 로 허용해
  // "입력된 금액에 최소 입찰 단위만큼 더한다"를 그대로 따른다.
  const rawAmount = (() => {
    const normalized = amount.trim().replaceAll(",", "");
    if (!/^\d+$/.test(normalized)) return null;
    const value = Number(normalized);
    return Number.isSafeInteger(value) ? value : null;
  })();

  const onAmountKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    // 콤마는 3자리마다 화면에 붙여주는 표기라, 사용자가 직접 찍지는 못하게 막는다.
    // 숫자 외 글자도 같이 막는다. 단축키·Backspace 같은 조작 키는 그대로 통과시킨다.
    const isTypingCharacter =
      event.key.length === 1 &&
      !event.ctrlKey &&
      !event.metaKey &&
      !event.altKey;
    if (isTypingCharacter && !/\d/.test(event.key)) {
      event.preventDefault();
    }
  };

  const onAmountChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { value, selectionStart } = event.target;
    const caret = selectionStart ?? value.length;
    // 커서 앞의 숫자 개수를 기준으로 잡아두면 콤마가 끼거나 빠져도 같은 자리로 돌아온다.
    amountCaretRef.current = value.slice(0, caret).replace(/\D/g, "").length;
    setAmount(formatAmountInput(value));
  };

  useLayoutEffect(() => {
    const digitCount = amountCaretRef.current;
    if (digitCount === null) return;
    amountCaretRef.current = null;
    const caret = caretPositionAfterDigits(amount, digitCount);
    amountInputRef.current?.setSelectionRange(caret, caret);
  }, [amount]);

  const onIncrementClick = () => {
    setAmount(formatAmountInput(String((rawAmount ?? currentPrice) + minUnit)));
  };

  // 실시간 입찰 목록 — 개수 제한 없이 최신순으로 이어서 불러온다(스크롤 페이지네이션).
  // 입찰자별 중복 제거(같은 회원의 최신 입찰만 표시)는 RealtimeBidList가 담당한다.
  // 종료된 경매의 입찰 내역은 판매자만 볼 수 있다(서버가 403). 이 화면은 종료를 감지하면
  // 결과 화면으로 보내므로, 그 사이에 굳이 조회해서 403을 만들지 않는다.
  const isEnded = auction.status === AuctionStatus.ENDED;
  const previewBidsQuery = useInfiniteQuery({
    queryKey: ["auction-bids", auction.id, "preview"],
    queryFn: ({ pageParam }: { pageParam?: string }) =>
      getAuctionBids(auction.id, {
        size: REALTIME_BID_PAGE_SIZE,
        cursor: pageParam,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.cursor : undefined,
    enabled: !isEnded,
    staleTime: 0,
    refetchInterval: () =>
      auction.status === AuctionStatus.LIVE &&
      document.visibilityState !== "hidden"
        ? pollingInterval()
        : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const previewBidItems =
    previewBidsQuery.data?.pages.flatMap((page) => page.items) ?? [];
  const allBidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id, "all"],
    queryFn: () => getAuctionBids(auction.id, { size: BID_MODAL_SIZE }),
    enabled: allBidsOpen && !isEnded,
  });
  const topPreviewBid = previewBidItems[0];
  const latestBidId = topPreviewBid ? Number(topPreviewBid.id) : undefined;

  // 실시간 화면에서 추월당했는지 판단하려면 "내 최고 입찰가"를 알아야 한다.
  // 페이지를 새로 열었을 때는 입찰 내역에서, 직접 입찰했을 때는 그 결과에서 채운다.
  const myHighestBidRef = useRef<{ bidId: number; price: number } | null>(null);
  // 방금 접수한 입찰 요청의 id. 성공 브로드캐스트가 이 id와 일치하면 "내 요청"으로 판단해
  // 성공 토스트를 띄운다 — 이 화면은 서버로부터 자신의 memberId를 알 방법이 없다.
  const pendingBidRequestIdRef = useRef<number | null>(null);
  // 목록 최상단(=현재 최고가)이 내 입찰일 때만 "내가 선두"라고 무장한다. 최근 목록 안에서
  // 내 입찰을 찾는 방식은 쓰지 않는다 — 이미 추월당해 ref가 null로 초기화된 뒤에도, 폴링이나
  // 창 포커스로 목록이 다시 조회될 때마다 그 목록에 남아있는 예전(이미 밀린) 내 입찰로 ref가
  // 재무장되어, 그다음 새 입찰이 들어올 때마다 같은 추월 상황에 대해 "추월당했습니다" 알림이
  // 중복으로 뜨는 버그가 있었다. 최상단 여부로 제한하면 선두를 잃은 동안에는 재무장되지 않는다.
  useEffect(() => {
    if (topPreviewBid?.isMine) {
      myHighestBidRef.current = {
        bidId: Number(topPreviewBid.id),
        price: topPreviewBid.amount,
      };
    }
  }, [topPreviewBid]);

  const refreshSnapshot = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ["auction-detail", auction.id],
    });
    void queryClient.invalidateQueries({
      queryKey: ["auction-bids", auction.id],
    });
  }, [auction.id, queryClient]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        refreshSnapshot();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () =>
      document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [refreshSnapshot]);

  const applyBidUpdate = useCallback(
    (message: AuctionBidUpdatedMessage) => {
      // 브로드캐스트에는 memberId가 없어 닉네임으로 본인 여부를 판단한다. bidId를 기억해뒀다가
      // 비교하는 방식은 이 탭에서 낸 요청에만 정확했다 — 다른 탭/기기에서 낸 내 입찰이거나,
      // 새로고침 시점에 이미 추월당해 있던 경우엔 갱신되지 않아 실시간 목록에 "나"와 내 닉네임이
      // 서로 다른 사람처럼 중복 표시되는 버그가 있었다.
      const isMine = !!myNickname && message.latestBid.nickname === myNickname;

      if (
        message.bidRequestId !== null &&
        message.bidRequestId === pendingBidRequestIdRef.current
      ) {
        pendingBidRequestIdRef.current = null;
        setIsBidRequestPending(false);
        setAmount("");
        queryClient.invalidateQueries({
          queryKey: getGetMyPointBalanceQueryKey(),
        });
        toast.success("입찰 성공", {
          description: `${formatWon(message.currentPrice)}에 입찰했습니다.`,
        });
      } else if (!isMine) {
        const myHighestBid = myHighestBidRef.current;
        if (
          myHighestBid &&
          message.latestBid.bidId !== myHighestBid.bidId &&
          message.currentPrice > myHighestBid.price
        ) {
          myHighestBidRef.current = null;
          toast.warning("추월당했습니다", {
            description: `다른 회원이 ${formatWon(message.currentPrice)}에 입찰했습니다.`,
          });
        }
      }

      if (isMine) {
        myHighestBidRef.current = {
          bidId: message.latestBid.bidId,
          price: message.currentPrice,
        };
      }

      setRealtimeSnapshot((current) => ({
        auctionId: auction.id,
        price:
          current.auctionId === auction.id
            ? Math.max(current.price, message.currentPrice)
            : message.currentPrice,
        endsAt:
          current.auctionId === auction.id
            ? laterEndTime(current.endsAt, message.endedAt)
            : (message.endedAt ?? undefined),
      }));

      queryClient.setQueryData<
        InfiniteData<AuctionBidsSnapshot, string | undefined> | undefined
      >(["auction-bids", auction.id, "preview"], (snapshot) => {
        if (!snapshot || snapshot.pages.length === 0) return snapshot;

        const [firstPage, ...remainingPages] = snapshot.pages;
        const updatedFirstPage = mergeLatestBid(
          firstPage,
          message.latestBid,
          isMine,
          REALTIME_BID_PAGE_SIZE,
        );
        if (!updatedFirstPage) return snapshot;

        const latestBidId = String(message.latestBid.bidId);
        return {
          ...snapshot,
          pages: [
            updatedFirstPage,
            ...remainingPages.map((page) => ({
              ...page,
              items: page.items.filter((item) => item.id !== latestBidId),
            })),
          ],
        };
      });
      queryClient.setQueryData<AuctionBidsSnapshot | undefined>(
        ["auction-bids", auction.id, "all"],
        (snapshot) =>
          mergeLatestBid(snapshot, message.latestBid, isMine, BID_MODAL_SIZE),
      );
    },
    [auction.id, myNickname, queryClient],
  );

  const applyBidFailure = useCallback(
    (message: BidRequestFailedMessage) => {
      // 내가 방금 보낸 요청의 결과만 반영한다.
      if (
        pendingBidRequestIdRef.current !== null &&
        message.bidRequestId !== pendingBidRequestIdRef.current
      ) {
        return;
      }
      pendingBidRequestIdRef.current = null;
      setIsBidRequestPending(false);
      setFail(message.failureMessage);
      refreshSnapshot();
    },
    [refreshSnapshot],
  );

  const connectionStatus = useAuctionBidUpdates({
    auctionId: auction.id,
    latestBidId,
    onBidUpdated: applyBidUpdate,
    onBidFailed: applyBidFailure,
    onSubscribed: refreshSnapshot,
  });

  useEffect(() => {
    if (!isBidRequestPending) return;

    const bidRequestId = pendingBidRequestIdRef.current;
    if (bidRequestId === null) return;

    let cancelled = false;
    let pollTimerId: number | undefined;

    const isStillPending = () =>
      !cancelled && pendingBidRequestIdRef.current === bidRequestId;

    const clearPendingRequest = () => {
      pendingBidRequestIdRef.current = null;
      setIsBidRequestPending(false);
    };

    const pollResult = async () => {
      try {
        const result = await getBidRequestResult(auction.id, bidRequestId);
        if (!isStillPending()) return;

        if (result.status === "SUCCEEDED") {
          clearPendingRequest();
          setAmount("");
          refreshSnapshot();
          void queryClient.invalidateQueries({
            queryKey: getGetMyPointBalanceQueryKey(),
          });
          toast.success("입찰 성공", {
            description: `${formatWon(result.bidPrice)}에 입찰했습니다.`,
          });
          return;
        }

        if (result.status === "FAILED") {
          clearPendingRequest();
          refreshSnapshot();
          setFail(
            result.failureMessage ??
              "입찰에 실패했습니다. 잠시 후 다시 시도해 주세요.",
          );
          return;
        }
      } catch {
        // 일시적인 조회 실패는 다음 폴링에서 재시도한다.
      }

      if (isStillPending()) {
        pollTimerId = window.setTimeout(
          pollResult,
          BID_REQUEST_RESULT_POLL_INTERVAL_MILLIS,
        );
      }
    };

    pollTimerId = window.setTimeout(
      pollResult,
      BID_REQUEST_RESULT_POLL_INTERVAL_MILLIS,
    );

    const timeoutId = window.setTimeout(() => {
      if (!isStillPending()) return;
      clearPendingRequest();
      refreshSnapshot();
      toast.error("입찰 결과 확인 지연", {
        description:
          "처리 결과를 받지 못했습니다. 포인트와 입찰 내역을 확인해 주세요.",
      });
    }, BID_REQUEST_RESULT_TIMEOUT_MILLIS);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
      if (pollTimerId !== undefined) window.clearTimeout(pollTimerId);
    };
  }, [auction.id, isBidRequestPending, queryClient, refreshSnapshot]);

  const bidMutation = useMutation({
    mutationFn: (bidPrice: number) => createBidRequest(auction.id, bidPrice),
  });

  const placeBid = useCallback(() => {
    if (parsedAmount === null) return;
    bidMutation.mutate(parsedAmount, {
      onSuccess: (placed) => {
        // 접수만 된 상태다 — 실제 처리 결과(성공/실패)는 WebSocket으로 비동기 도착한다.
        // 아직 입력값을 지우지 않는다: 결과를 못 받아 타임아웃될 경우에도 같은 금액으로
        // 바로 재시도할 수 있어야 한다. 실제 성공이 확인되면 applyBidUpdate에서 지운다.
        pendingBidRequestIdRef.current = placed.bidRequestId;
        setIsBidRequestPending(true);
      },
      onError: (error) => setFail(getBidErrorMessage(error)),
    });
  }, [bidMutation, parsedAmount]);

  const closeBidConfirm = useCallback(() => {
    setConfirmOpen(false);
    setDontShowConfirmAgain(false);
  }, []);

  const onBidClick = () => {
    if (parsedAmount === null) {
      setFail("입찰가는 현재가 + 최소 입찰 단위 이상이어야 합니다.");
      return;
    }
    if (shouldSkipBidConfirm()) {
      placeBid();
      return;
    }
    setConfirmOpen(true);
  };

  const confirmBid = useCallback(() => {
    if (parsedAmount === null) return;
    if (dontShowConfirmAgain) setSkipBidConfirm(true);
    closeBidConfirm();
    placeBid();
  }, [closeBidConfirm, dontShowConfirmAgain, parsedAmount, placeBid]);

  const endTransitionTimeoutRef = useRef<number | null>(null);

  // 경매가 끝나는 순간 바로 화면을 갈아치우면 결과가 툭 튀어나오는 느낌을 준다.
  // 짧게 지연했다가 전환해 종료 → 결과 화면 전환이 자연스럽게 이어지도록 한다.
  const goEnd = useCallback(() => {
    if (endTransitionTimeoutRef.current !== null) return;
    endTransitionTimeoutRef.current = window.setTimeout(() => {
      navigate({
        to: "/auctions/$auctionId/end",
        params: { auctionId: auction.id },
      });
    }, AUCTION_END_TRANSITION_DELAY_MILLIS);
  }, [auction.id, navigate]);

  useEffect(() => {
    return () => {
      if (endTransitionTimeoutRef.current !== null) {
        window.clearTimeout(endTransitionTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (auction.status === AuctionStatus.ENDED) {
      goEnd();
    }
  }, [auction.status, goEnd]);

  return (
    <PageContainer className="grid gap-8 md:grid-cols-[1fr_380px]">
      {/* 좌: 카드 + 현재가/타이머 */}
      <div className="flex flex-col gap-6">
        <div className="grid gap-6 sm:grid-cols-[220px_1fr]">
          <CardThumb
            cardName={auction.cardName}
            grade={auction.grade}
            imageUrl={auction.thumbnailUrl}
          />
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <GradeBadge grade={auction.grade} />
              <ConnectionStatus status={connectionStatus} />
            </div>
            <h1 className="text-2xl font-bold">
              {auction.title ?? auction.cardName}
            </h1>
            {auction.title && (
              <p className="text-sm text-[var(--color-text-sub)]">
                {auction.cardName}
              </p>
            )}
            <div className="flex items-center gap-2 text-sm text-[var(--color-text-sub)]">
              <Avatar
                src={auction.sellerProfileImageUrl}
                fallbackSrc={pokemonAvatarForKey(
                  auction.sellerId ?? auction.sellerNickname ?? "판매자",
                )}
                nickname={auction.sellerNickname || "판매자"}
                className="size-7"
                initialClassName="text-xs"
              />
              <span>
                판매자 · {auction.sellerNickname || "검증된 위탁 상품"}
              </span>
            </div>
            <div className="mt-2 flex items-end justify-between rounded-[var(--radius-lg)] border border-border bg-card p-5">
              <Price amount={currentPrice} label="현재가" size="lg" />
              <div className="flex flex-col items-end gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  남은 시간
                </span>
                <Countdown to={endsAt} onEnd={goEnd} />
              </div>
            </div>
          </div>
        </div>

        {/* 입찰 입력 */}
        {isAuthenticated ? (
          <div className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2 text-[var(--color-text-sub)]">
                최소 다음 입찰가
                {isMyAuction && (
                  <span className="rounded-[var(--radius-pill)] bg-[var(--color-surface-2)] px-2 py-0.5 text-xs text-[var(--color-text-muted)]">
                    자신의 상품
                  </span>
                )}
              </span>
              <span className="tabular font-semibold text-foreground">
                {formatWon(minNext)}
              </span>
            </div>
            <div className="flex gap-2">
              <Input
                ref={amountInputRef}
                inputMode="numeric"
                value={amount}
                onKeyDown={onAmountKeyDown}
                onChange={onAmountChange}
                placeholder={`${minNext.toLocaleString("ko-KR")} 이상`}
                className="tabular"
                disabled={isMyAuction}
              />
              <Button
                type="button"
                variant="secondary"
                size="icon"
                onClick={onIncrementClick}
                disabled={
                  isMyAuction ||
                  minUnit <= 0 ||
                  bidMutation.isPending ||
                  isBidRequestPending
                }
                aria-label={`최소 입찰 단위(${formatWon(minUnit)})만큼 추가`}
                className="shrink-0"
              >
                <span aria-hidden="true">+</span>
                <span className="sr-only">
                  현재 최소 입찰 단위 {formatWon(minUnit)} 추가
                </span>
              </Button>
              <Button
                onClick={onBidClick}
                disabled={
                  isMyAuction ||
                  parsedAmount === null ||
                  bidMutation.isPending ||
                  isBidRequestPending
                }
                className="shrink-0"
              >
                {bidMutation.isPending || isBidRequestPending
                  ? "처리 중…"
                  : "입찰하기"}
              </Button>
            </div>
            <div className="flex flex-wrap gap-2" aria-label="추천 입찰 금액">
              {recommendedAmounts.map((recommended, index) => (
                <Button
                  key={`${recommended}-${index}`}
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() =>
                    setAmount(formatAmountInput(String(recommended)))
                  }
                  disabled={isMyAuction}
                >
                  {formatWon(recommended)}
                </Button>
              ))}
            </div>
            <p className="text-xs text-[var(--color-text-muted)]">
              {isMyAuction
                ? "자신이 등록한 경매에는 입찰할 수 없습니다."
                : "입찰은 취소할 수 없습니다."}
            </p>
          </div>
        ) : (
          <div className="flex items-center justify-between rounded-[var(--radius-lg)] border border-border bg-card p-5">
            <p className="text-sm text-[var(--color-text-sub)]">
              입찰하려면 로그인이 필요합니다.
            </p>
            <Button asChild size="sm">
              <Link to="/login">로그인</Link>
            </Button>
          </div>
        )}
      </div>

      {/* 우: 실시간 입찰 목록(입찰자별 최신 입찰만, 스크롤로 이어서 로드) + 전체 모달 */}
      <aside className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">실시간 순위</h2>
          <button
            type="button"
            onClick={() => setAllBidsOpen(true)}
            className="text-sm font-semibold text-primary hover:underline"
          >
            전체
          </button>
        </div>
        {previewBidsQuery.isPending ? (
          <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
            불러오는 중입니다.
          </p>
        ) : (
          <RealtimeBidList
            bids={previewBidItems}
            hasNext={previewBidsQuery.hasNextPage}
            isFetchingNextPage={previewBidsQuery.isFetchingNextPage}
            onLoadMore={() => void previewBidsQuery.fetchNextPage()}
          />
        )}
      </aside>

      {/* 전체 입찰 모달 */}
      <Dialog open={allBidsOpen} onOpenChange={setAllBidsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>전체 입찰 내역</DialogTitle>
          </DialogHeader>
          {allBidsQuery.isPending ? (
            <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
              불러오는 중입니다.
            </p>
          ) : (
            <BidList
              bids={allBidsQuery.data?.items ?? []}
              className="max-h-96 overflow-y-auto"
            />
          )}
        </DialogContent>
      </Dialog>

      {/* 입찰 확인 모달 */}
      <Dialog
        open={confirmOpen}
        onOpenChange={(open) =>
          open ? setConfirmOpen(true) : closeBidConfirm()
        }
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>입찰 확인</DialogTitle>
            <DialogDescription>
              입찰은 취소할 수 없습니다. 금액을 확인해 주세요.
            </DialogDescription>
          </DialogHeader>
          <dl className="flex flex-col gap-2 rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">현재가</dt>
              <dd className="tabular">{formatWon(currentPrice)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">입찰 금액</dt>
              <dd className="tabular font-bold text-primary">
                {formatWon(parsedAmount ?? 0)}
              </dd>
            </div>
          </dl>
          <div className="flex items-center gap-2">
            <input
              id="skip-bid-confirm"
              type="checkbox"
              checked={dontShowConfirmAgain}
              onChange={(e) => setDontShowConfirmAgain(e.target.checked)}
              className="size-4 rounded border border-[var(--color-border-strong)] accent-primary"
            />
            <Label htmlFor="skip-bid-confirm" className="text-sm">
              다시 보지 않기
            </Label>
          </div>
          <DialogFooter>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={closeBidConfirm}
            >
              취소
            </Button>
            <Button
              className="flex-1"
              onClick={confirmBid}
              disabled={bidMutation.isPending}
            >
              입찰하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 입찰 실패 모달 */}
      <Dialog open={fail != null} onOpenChange={(o) => !o && setFail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="text-[var(--color-danger)]">
              입찰 실패
            </DialogTitle>
            <DialogDescription>{fail}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button className="w-full" onClick={() => setFail(null)}>
              확인
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
