import { Link } from "@tanstack/react-router";
import { cn } from "@/lib/utils";
import type { AuctionSummary } from "@/lib/types";
import { AuctionStatus } from "@/lib/types";
import { formatDateTime, formatWon } from "@/lib/format";
import { CardThumb } from "./card-thumb";
import { StatusBadge } from "./status-badge";
import { GradeBadge } from "./grade-badge";
import { HeartButton, WatchButton } from "./heart-button";
import { Countdown } from "./countdown";
import { Price } from "./price";

/** 경매/상품 카드 (DESIGN.md §5.3) */
export function AuctionCard({
  auction,
  className,
}: {
  auction: AuctionSummary;
  className?: string;
}) {
  const { status } = auction;
  const isLive = status === AuctionStatus.LIVE;
  const isUpcoming = status === AuctionStatus.UPCOMING;
  const won = (auction.currentPrice ?? 0) > 0;

  return (
    <Link
      to="/auctions/$auctionId"
      params={{ auctionId: auction.id }}
      className={cn(
        "group flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-3 transition-colors hover:border-[var(--color-border-strong)]",
        className,
      )}
    >
      <div className="relative">
        <CardThumb
          cardName={auction.cardName}
          grade={auction.grade}
          imageUrl={auction.thumbnailUrl}
        />
        <StatusBadge status={status} className="absolute top-2 left-2" />
        {auction.watched == null ? (
          <HeartButton
            count={auction.watchCount}
            defaultActive={isUpcoming && (auction.watchCount ?? 0) > 200}
            className="absolute top-2 right-2"
          />
        ) : (
          <WatchButton
            auctionId={auction.id}
            watched={auction.watched}
            count={auction.watchCount}
            className="absolute top-2 right-2"
          />
        )}
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <GradeBadge grade={auction.grade} />
        </div>
        <h3 className="line-clamp-1 text-sm font-semibold">
          {auction.cardName}
        </h3>

        {isLive && (
          <div className="flex flex-wrap items-end justify-between gap-x-2 gap-y-1">
            <Price amount={auction.currentPrice} label="현재가" size="md" />
            <div className="ml-auto flex flex-col items-end gap-0.5">
              <span className="text-xs text-[var(--color-text-muted)]">
                남은 시간
              </span>
              <Countdown to={auction.endsAt} className="text-base" />
            </div>
          </div>
        )}

        {isUpcoming && (
          <div className="flex items-end justify-between">
            <Price
              amount={auction.startPrice}
              label="시작가"
              size="md"
              emphasize={false}
            />
            <div className="flex flex-col items-end gap-0.5">
              <span className="text-xs text-[var(--color-text-muted)]">
                시작 시각
              </span>
              <span className="tabular text-xs text-[var(--color-text-sub)]">
                {formatDateTime(auction.startsAt)}
              </span>
            </div>
          </div>
        )}

        {status === AuctionStatus.ENDED && (
          <div className="flex items-end justify-between">
            <div className="flex flex-col gap-0.5">
              <span className="text-xs text-[var(--color-text-muted)]">
                {won ? "낙찰가" : "결과"}
              </span>
              <span
                className={cn(
                  "tabular text-lg font-bold",
                  won
                    ? "text-[var(--color-price)]"
                    : "text-[var(--color-text-muted)]",
                )}
              >
                {won ? formatWon(auction.currentPrice) : "유찰"}
              </span>
            </div>
            <span className="text-xs text-[var(--color-text-muted)]">
              종료됨
            </span>
          </div>
        )}
      </div>
    </Link>
  );
}
