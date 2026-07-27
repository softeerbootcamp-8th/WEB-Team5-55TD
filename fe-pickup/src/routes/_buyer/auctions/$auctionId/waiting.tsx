import { useCallback } from "react";
import { createFileRoute, notFound, useNavigate } from "@tanstack/react-router";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Countdown } from "@/components/domain/countdown";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { auctionDetails, currentUser } from "@/lib/mock/data";
import { formatDateTime, formatPoint, formatWon } from "@/lib/format";

export const Route = createFileRoute("/_buyer/auctions/$auctionId/waiting")({
  loader: ({ params }) => {
    const auction = auctionDetails[params.auctionId];
    if (!auction) throw notFound();
    return { auction };
  },
  component: WaitingPage,
});

/** DESIGN.md · waiting.html — 시작 시각 도래 시 자동으로 실시간 경매로 전환 */
function WaitingPage() {
  const { auction } = Route.useLoaderData();
  const navigate = useNavigate();

  const goLive = useCallback(() => {
    navigate({
      to: "/auctions/$auctionId/live",
      params: { auctionId: auction.id },
    });
  }, [auction.id, navigate]);

  return (
    <PageContainer className="flex flex-col items-center gap-8 py-16">
      <Badge variant="warning">경매 대기</Badge>

      <CardThumb
        cardName={auction.cardName}
        grade={auction.grade}
        className="w-56"
      />

      <div className="flex flex-col items-center gap-2 text-center">
        <GradeBadge grade={auction.grade} />
        <h1 className="text-2xl font-bold">{auction.cardName}</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          시작 시각 {formatDateTime(auction.startsAt)}
        </p>
      </div>

      <div className="flex flex-col items-center gap-2">
        <span className="text-xs text-[var(--color-text-muted)]">
          시작까지 남은 시간
        </span>
        <Countdown to={auction.startsAt} onEnd={goLive} className="text-3xl" />
        <p className="mt-1 text-xs text-[var(--color-text-muted)]">
          시작 시각이 되면 자동으로 실시간 경매로 이동합니다.
        </p>
      </div>

      <dl className="grid w-full max-w-md grid-cols-3 gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5 text-center">
        <Stat label="시작가" value={formatWon(auction.startPrice)} />
        <Stat label="최소 입찰 단위" value={formatWon(auction.minBidUnit)} />
        <Stat
          label="보유 포인트"
          value={formatPoint(currentUser.points)}
          accent
        />
      </dl>

      <Button size="lg" disabled className="w-full max-w-md">
        시작 전 · 입찰 대기 중
      </Button>
    </PageContainer>
  );
}

function Stat({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
      <dd
        className={
          "tabular text-sm font-semibold " +
          (accent ? "text-primary" : "text-foreground")
        }
      >
        {value}
      </dd>
    </div>
  );
}
