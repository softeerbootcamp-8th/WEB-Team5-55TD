import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { AxiosError } from "axios";
import { CheckCircle2, XCircle } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { ResultBadge } from "@/components/domain/status-badge";
import { Button } from "@/components/ui/button";
import { getAuctionDetail } from "@/api/auctions";
import { formatWon } from "@/lib/format";

export const Route = createFileRoute("/_buyer/auctions/$auctionId/end")({
  loader: async ({ params }) => {
    try {
      return { auction: await getAuctionDetail(params.auctionId) };
    } catch (error) {
      if (error instanceof AxiosError && error.response?.status === 404) {
        throw notFound();
      }
      throw error;
    }
  },
  component: AuctionEndPage,
});

/**
 * DESIGN.md · auction end.html — 낙찰/유찰 결과.
 * 백엔드가 낙찰자 회원 id를 내려주지 않아 "내가 낙찰됐는지"는 판별할 수 없다.
 * 경매 전체의 낙찰/유찰 결과만 정확히 표시한다.
 */
function AuctionEndPage() {
  const { auction } = Route.useLoaderData();
  const won = auction.won;

  return (
    <PageContainer className="flex flex-col items-center gap-8 py-16">
      <div className="flex flex-col items-center gap-3 text-center">
        {won ? (
          <CheckCircle2 className="size-14 text-[var(--color-success)]" />
        ) : (
          <XCircle className="size-14 text-[var(--color-text-muted)]" />
        )}
        <h1 className="text-3xl font-bold">
          {won ? "낙찰되었습니다" : "유찰되었습니다"}
        </h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          {won ? "경매가 낙찰로 종료되었습니다." : "낙찰 없이 종료되었습니다."}
        </p>
      </div>

      <div className="flex w-full max-w-md flex-col items-center gap-5 rounded-[var(--radius-lg)] border border-border bg-card p-6">
        <CardThumb
          cardName={auction.cardName}
          grade={auction.grade}
          imageUrl={auction.thumbnailUrl}
          className="w-40"
        />
        <div className="flex items-center gap-2">
          <ResultBadge won={won} />
          <GradeBadge grade={auction.grade} />
        </div>
        <h2 className="text-lg font-semibold">{auction.cardName}</h2>

        <dl className="w-full divide-y divide-border">
          <RowLine
            label={won ? "최종 낙찰가" : "결과"}
            value={won ? formatWon(auction.currentPrice) : "유찰"}
            emphasize
          />
        </dl>
      </div>

      <div className="flex w-full max-w-md gap-3">
        <Button variant="secondary" className="flex-1" asChild>
          <Link to="/auctions">다른 경매</Link>
        </Button>
        <Button className="flex-1" asChild>
          <Link to="/bids">낙찰 내역</Link>
        </Button>
      </div>
    </PageContainer>
  );
}

function RowLine({
  label,
  value,
  emphasize,
}: {
  label: string;
  value: string;
  emphasize?: boolean;
}) {
  return (
    <div className="flex items-center justify-between py-3 text-sm">
      <dt className="text-[var(--color-text-sub)]">{label}</dt>
      <dd
        className={
          "tabular font-semibold " +
          (emphasize ? "text-[var(--color-price)] text-base" : "text-foreground")
        }
      >
        {value}
      </dd>
    </div>
  );
}
