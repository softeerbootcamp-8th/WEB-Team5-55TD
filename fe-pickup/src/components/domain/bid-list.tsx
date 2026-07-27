import { cn } from "@/lib/utils";
import type { Bid } from "@/lib/types";
import { formatWon, relativeTime } from "@/lib/format";

/** 입찰 내역 행 (DESIGN.md §5.9). 본인 입찰은 액센트 강조. */
export function BidRow({ bid }: { bid: Bid }) {
  const mine = bid.isMine;
  return (
    <li
      className={cn(
        "flex items-center justify-between rounded-[var(--radius-sm)] px-3 py-2.5 text-sm",
        mine
          ? "bg-[var(--color-buyer-weak)] font-semibold"
          : "odd:bg-[var(--color-surface-2)]/40",
      )}
    >
      <span
        className={cn(mine ? "text-primary" : "text-[var(--color-text-sub)]")}
      >
        {mine ? "나" : bid.maskedNickname}
      </span>
      <span className="tabular flex-1 pr-3 text-right font-semibold text-foreground">
        {formatWon(bid.amount)}
      </span>
      <span className="w-14 shrink-0 text-right text-xs text-[var(--color-text-muted)]">
        {relativeTime(bid.createdAt)}
      </span>
    </li>
  );
}

/** 입찰 내역 리스트 */
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
