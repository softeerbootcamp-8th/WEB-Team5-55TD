import { AnimatePresence, motion } from "framer-motion";
import { cn } from "@/lib/utils";
import type { Bid } from "@/lib/types";
import { formatWon, relativeTime } from "@/lib/format";
import { bidderKey, dedupeBidsByBidder } from "@/lib/bids";
import { Avatar } from "@/components/domain/avatar";
import { useAnimatedNumber } from "@/hooks/use-animated-number";
import { useLoadMoreSentinel } from "@/hooks/use-load-more-sentinel";

function displayNameOf(bid: Bid): string {
  return bid.isMine ? "나" : bid.nickname;
}

/** 입찰 내역 행 (DESIGN.md §5.9). 본인 입찰은 액센트 강조. */
export function BidRow({ bid }: { bid: Bid }) {
  const mine = bid.isMine;
  const displayName = displayNameOf(bid);
  return (
    <li
      className={cn(
        "flex items-center gap-2.5 rounded-[var(--radius-sm)] px-3 py-2.5 text-sm",
        mine
          ? "bg-[var(--color-buyer-weak)] font-semibold"
          : "odd:bg-[var(--color-surface-2)]/40",
      )}
    >
      <Avatar
        nickname={displayName}
        className="size-8 shrink-0"
        initialClassName="text-xs"
      />
      <span
        className={cn(
          "min-w-0 flex-1 truncate",
          mine ? "text-primary" : "text-[var(--color-text-sub)]",
        )}
      >
        {displayName}
      </span>
      <span className="tabular shrink-0 text-right font-semibold text-foreground">
        {formatWon(bid.amount)}
      </span>
      <span className="w-14 shrink-0 text-right text-xs text-[var(--color-text-muted)]">
        {relativeTime(bid.createdAt)}
      </span>
    </li>
  );
}

/** 입찰 내역 리스트 (전체 내역 등 정적 목록 — 중복 제거·애니메이션 없이 있는 그대로 표시) */
export function BidList({
  bids,
  className,
}: {
  bids: Bid[];
  className?: string;
}) {
  if (bids.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
        아직 입찰이 없습니다.
      </p>
    );
  }
  return (
    <ul className={cn("flex flex-col gap-1", className)}>
      {bids.map((b) => (
        <BidRow key={b.id} bid={b} />
      ))}
    </ul>
  );
}

/**
 * 실시간 목록 전용 행. 같은 입찰자 키를 유지한 채(React가 같은 컴포넌트로 인식) 금액이
 * 바뀌면 숫자가 카운트업/다운되고, `layout`으로 목록 내 위치 이동(맨 위로)도 함께
 * 애니메이션된다. 새로 등장하는 입찰자는 아래에서 위로 슬라이드하며 나타난다.
 */
function AnimatedBidRow({ bid }: { bid: Bid }) {
  const mine = bid.isMine;
  const displayName = displayNameOf(bid);
  const animatedAmount = useAnimatedNumber(bid.amount);

  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 36 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ type: "spring", stiffness: 420, damping: 32, mass: 0.7 }}
      className={cn(
        "flex items-center gap-2.5 rounded-[var(--radius-sm)] px-3 py-2.5 text-sm",
        mine
          ? "bg-[var(--color-buyer-weak)] font-semibold"
          : "odd:bg-[var(--color-surface-2)]/40",
      )}
    >
      <Avatar
        nickname={displayName}
        className="size-8 shrink-0"
        initialClassName="text-xs"
      />
      <span
        className={cn(
          "min-w-0 flex-1 truncate",
          mine ? "text-primary" : "text-[var(--color-text-sub)]",
        )}
      >
        {displayName}
      </span>
      <span className="tabular shrink-0 text-right font-semibold text-foreground">
        {formatWon(animatedAmount)}
      </span>
      <span className="w-14 shrink-0 text-right text-xs text-[var(--color-text-muted)]">
        {relativeTime(bid.createdAt)}
      </span>
    </motion.li>
  );
}

/**
 * 경매 상세의 "실시간 입찰 목록" (DESIGN.md §5.9).
 *
 * 입찰자별로 최신 입찰 하나만 보여준다 — 같은 회원이 다시 입찰하면 새 행이 추가되는
 * 대신 기존 행이 갱신되며 맨 위로 이동한다("전체 내역"은 이 중복 제거 없이 모든 입찰을
 * 보여준다, `BidList` 참고). 개수 제한이 없으므로 `hasNext`가 true인 동안 스크롤 시
 * `onLoadMore`로 다음 페이지를 이어서 불러온다.
 */
export function RealtimeBidList({
  bids,
  hasNext,
  isFetchingNextPage,
  onLoadMore,
  className,
}: {
  bids: Bid[];
  hasNext: boolean;
  isFetchingNextPage: boolean;
  onLoadMore: () => void;
  className?: string;
}) {
  const sentinelRef = useLoadMoreSentinel({
    enabled: hasNext && !isFetchingNextPage,
    onIntersect: onLoadMore,
  });
  const deduped = dedupeBidsByBidder(bids);

  if (deduped.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
        아직 입찰이 없습니다.
      </p>
    );
  }

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <ul className="flex flex-col gap-1">
        <AnimatePresence initial={false} mode="popLayout">
          {deduped.map((bid) => (
            <AnimatedBidRow key={bidderKey(bid)} bid={bid} />
          ))}
        </AnimatePresence>
      </ul>
      {hasNext && (
        <>
          <div ref={sentinelRef} aria-hidden className="h-px" />
          <button
            type="button"
            onClick={onLoadMore}
            disabled={isFetchingNextPage}
            className="rounded-[var(--radius-sm)] py-2 text-center text-xs font-medium text-[var(--color-text-sub)] hover:bg-[var(--color-surface-2)]/60 disabled:opacity-60"
          >
            {isFetchingNextPage ? "불러오는 중…" : "더 보기"}
          </button>
        </>
      )}
    </div>
  );
}
