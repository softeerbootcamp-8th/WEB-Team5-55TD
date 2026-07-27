import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { CheckCircle2, XCircle } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { ResultBadge } from "@/components/domain/status-badge";
import { Button } from "@/components/ui/button";
import { auctionDetails, currentUser } from "@/lib/mock/data";
import { formatPoint, formatWon, maskNickname } from "@/lib/format";

export const Route = createFileRoute("/_buyer/auctions/$auctionId/end")({
  loader: ({ params }) => {
    const auction = auctionDetails[params.auctionId];
    if (!auction) throw notFound();
    return { auction };
  },
  component: AuctionEndPage,
});

/** DESIGN.md · auction end.html — 낙찰/유찰 · 포인트 차감/반환 */
function AuctionEndPage() {
  const { auction } = Route.useLoaderData();
  const finalPrice = auction.currentPrice ?? 0;
  const won = finalPrice > 0; // 낙찰 성사 여부
  const iWon = won && auction.id === "a1"; // 데모: 본인 낙찰

  return (
    <PageContainer className="flex flex-col items-center gap-8 py-16">
      <div className="flex flex-col items-center gap-3 text-center">
        {iWon ? (
          <CheckCircle2 className="size-14 text-[var(--color-success)]" />
        ) : (
          <XCircle className="size-14 text-[var(--color-text-muted)]" />
        )}
        <h1 className="text-3xl font-bold">
          {iWon
            ? "낙찰되었습니다!"
            : won
              ? "경매가 종료되었습니다"
              : "유찰되었습니다"}
        </h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          {iWon
            ? "축하합니다. 마이페이지에서 낙찰 내역을 확인하세요."
            : won
              ? "다른 사용자가 낙찰했습니다."
              : "낙찰 없이 종료되었습니다."}
        </p>
      </div>

      <div className="flex w-full max-w-md flex-col items-center gap-5 rounded-[var(--radius-lg)] border border-border bg-card p-6">
        <CardThumb
          cardName={auction.cardName}
          grade={auction.grade}
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
            value={won ? formatWon(finalPrice) : "유찰"}
            emphasize
          />
          {won && (
            <RowLine
              label="낙찰자"
              value={iWon ? "나" : maskNickname("collector07")}
            />
          )}
          <RowLine
            label="포인트 처리"
            value={
              iWon ? `${formatWon(finalPrice)} 차감` : "전액 반환 (미낙찰)"
            }
            accent={iWon ? "danger" : "success"}
          />
          <RowLine
            label="현재 보유 포인트"
            value={formatPoint(currentUser.points)}
          />
        </dl>
      </div>

      <div className="flex w-full max-w-md gap-3">
        <Button variant="secondary" className="flex-1" asChild>
          <Link to="/auctions">다른 경매</Link>
        </Button>
        <Button className="flex-1" asChild>
          <Link to="/mypage">낙찰 내역</Link>
        </Button>
      </div>
    </PageContainer>
  );
}

function RowLine({
  label,
  value,
  emphasize,
  accent,
}: {
  label: string;
  value: string;
  emphasize?: boolean;
  accent?: "danger" | "success";
}) {
  return (
    <div className="flex items-center justify-between py-3 text-sm">
      <dt className="text-[var(--color-text-sub)]">{label}</dt>
      <dd
        className={
          "tabular font-semibold " +
          (accent === "danger"
            ? "text-[var(--color-danger)]"
            : accent === "success"
              ? "text-[var(--color-success)]"
              : emphasize
                ? "text-[var(--color-price)] text-base"
                : "text-foreground")
        }
      >
        {value}
      </dd>
    </div>
  );
}
