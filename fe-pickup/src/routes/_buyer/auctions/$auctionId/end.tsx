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

/** DESIGN.md · auction end.html — 낙찰/유찰 결과. 조회자 본인의 낙찰 여부를 기준으로 표시한다. */
function AuctionEndPage() {
  const { auction } = Route.useLoaderData();
  const sold = auction.won;
  const iWon = auction.myBidWon;

  const headline = iWon
    ? "낙찰되었습니다"
    : sold
      ? "낙찰자가 결정되었습니다"
      : "유찰되었습니다";
  const description = iWon
    ? "경매가 낙찰로 종료되었습니다."
    : sold
      ? "다른 회원이 낙찰받아 종료되었습니다."
      : "낙찰 없이 종료되었습니다.";

  return (
    <PageContainer className="flex flex-col items-center gap-8 py-16">
      <div className="flex flex-col items-center gap-3 text-center">
        {iWon ? (
          <CheckCircle2 className="size-14 text-[var(--color-success)]" />
        ) : (
          <XCircle className="size-14 text-[var(--color-text-muted)]" />
        )}
        <h1 className="text-3xl font-bold">{headline}</h1>
        <p className="text-sm text-[var(--color-text-sub)]">{description}</p>
      </div>

      <div className="flex w-full max-w-md flex-col items-center gap-5 rounded-[var(--radius-lg)] border border-border bg-card p-6">
        <CardThumb
          cardName={auction.cardName}
          grade={auction.grade}
          imageUrl={auction.thumbnailUrl}
          className="w-40"
        />
        <div className="flex items-center gap-2">
          <ResultBadge won={sold} />
          <GradeBadge grade={auction.grade} />
        </div>
        <h2 className="text-lg font-semibold">{auction.cardName}</h2>

        <dl className="w-full divide-y divide-border">
          <RowLine
            label={sold ? "최종 낙찰가" : "결과"}
            value={sold ? formatWon(auction.currentPrice) : "유찰"}
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
