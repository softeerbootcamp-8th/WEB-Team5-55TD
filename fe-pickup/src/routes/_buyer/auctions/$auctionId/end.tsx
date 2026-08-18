import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { AxiosError } from "axios";
import { motion } from "framer-motion";
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

/** 결과 화면 등장 연출 — 입찰 화면에서 곧장 전환되어도 결과가 부드럽게 나타나도록 한다. */
const REVEAL_TRANSITION = { type: "spring", stiffness: 420, damping: 32, mass: 0.7 } as const;

/** DESIGN.md · auction end.html — 낙찰/유찰 결과. 조회자 본인의 낙찰 여부를 기준으로 표시한다. */
function AuctionEndPage() {
  const { auction } = Route.useLoaderData();
  const sold = auction.won;
  const iWon = auction.myBidWon;

  // 낙찰자를 헤드라인에 직접 노출한다 — 본인이면 축하 문구, 타인이면 닉네임을 그대로 주어로 세운다.
  const headline = iWon
    ? "축하합니다!"
    : sold
      ? auction.winnerNicknameMasked
        ? `${auction.winnerNicknameMasked}님 낙찰!`
        : "낙찰자가 결정되었습니다"
      : "유찰되었습니다";
  const description = iWon
    ? `${auction.cardName}, 넌 내 거야!`
    : sold
      ? "다른 회원이 낙찰받아 종료되었습니다."
      : "낙찰 없이 종료되었습니다.";

  return (
    <PageContainer className="flex flex-col items-center gap-8 py-16">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={REVEAL_TRANSITION}
        className="flex flex-col items-center gap-3 text-center"
      >
        {iWon ? (
          <CheckCircle2 className="size-14 text-[var(--color-success)]" />
        ) : (
          <XCircle className="size-14 text-[var(--color-text-muted)]" />
        )}
        <h1 className="text-3xl font-bold">{headline}</h1>
        <p className="text-sm text-[var(--color-text-sub)]">{description}</p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ ...REVEAL_TRANSITION, delay: 0.08 }}
        className="flex w-full max-w-md flex-col items-center gap-5 rounded-[var(--radius-lg)] border border-border bg-card p-6"
      >
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
      </motion.div>

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
