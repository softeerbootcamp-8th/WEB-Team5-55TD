import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { AxiosError } from "axios";
import { ChevronLeft } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { StatusBadge } from "@/components/domain/status-badge";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { HeartButton } from "@/components/domain/heart-button";
import { Button } from "@/components/ui/button";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { getAuctionDetail } from "@/api/auctions";
import { AuctionStatus } from "@/lib/types";
import { formatDateTime, formatWon } from "@/lib/format";

export const Route = createFileRoute("/_buyer/auctions/$auctionId/")({
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
  component: AuctionDetailPage,
});

/** DESIGN.md · auction detail.html */
function AuctionDetailPage() {
  const { auction } = Route.useLoaderData();
  const isLive = auction.status === AuctionStatus.LIVE;
  const isUpcoming = auction.status === AuctionStatus.UPCOMING;
  const images = auction.images ?? [];

  return (
    <PageContainer className="flex flex-col gap-6">
      <Link
        to="/auctions"
        className="inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground"
      >
        <ChevronLeft className="size-4" /> 경매 목록
      </Link>

      <div className="grid gap-8 md:grid-cols-2">
        {/* 이미지 */}
        <div className="flex flex-col gap-3">
          <CardThumb
            cardName={auction.cardName}
            grade={auction.grade}
            imageUrl={images[0] ?? auction.thumbnailUrl}
            label={!images[0] && !auction.thumbnailUrl ? "앞면" : undefined}
            className="w-full"
          />
          {images.length > 1 && (
            <div className="grid grid-cols-4 gap-2">
              {images.map((img, i) => (
                <CardThumb
                  key={i}
                  cardName={auction.cardName}
                  imageUrl={img}
                  aspect="aspect-square"
                  label={i === 0 ? "앞" : i === 1 ? "뒤" : `${i + 1}`}
                />
              ))}
            </div>
          )}
        </div>

        {/* 정보 */}
        <div className="flex flex-col gap-5">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-2">
              <StatusBadge status={auction.status} />
              <GradeBadge grade={auction.grade} />
            </div>
            <HeartButton
              count={auction.watchCount}
              className="bg-[var(--color-surface-2)] !text-[var(--color-text-sub)]"
            />
          </div>

          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold">{auction.cardName}</h1>
            <p className="text-sm text-[var(--color-text-sub)]">
              {auction.sellerNickname
                ? `판매자 · ${auction.sellerNickname}`
                : "검증된 위탁 상품"}
            </p>
          </div>

          <div className="rounded-[var(--radius-lg)] border border-border bg-card p-5">
            {isLive ? (
              <div className="flex items-end justify-between">
                <Price amount={auction.currentPrice} label="현재가" size="lg" />
                <div className="flex flex-col items-end gap-0.5">
                  <span className="text-xs text-[var(--color-text-muted)]">
                    남은 시간
                  </span>
                  <Countdown to={auction.endsAt} />
                </div>
              </div>
            ) : isUpcoming ? (
              <div className="flex items-end justify-between">
                <Price
                  amount={auction.startPrice}
                  label="시작가"
                  size="lg"
                  emphasize={false}
                />
                <div className="flex flex-col items-end gap-0.5">
                  <span className="text-xs text-[var(--color-text-muted)]">
                    시작 시각
                  </span>
                  <span className="tabular text-sm text-[var(--color-text-sub)]">
                    {formatDateTime(auction.startsAt)}
                  </span>
                </div>
              </div>
            ) : (
              <Price amount={auction.currentPrice} label="낙찰가" size="lg" />
            )}
            {(isLive || isUpcoming) && (
              <p className="mt-3 text-xs text-[var(--color-text-muted)]">
                최소 입찰 단위 {formatWon(auction.minBidUnit)} · 시작가의 5%
              </p>
            )}
          </div>

          {/* 카드 상세 정보 (접이식) */}
          <Accordion type="single" collapsible defaultValue="info">
            <AccordionItem value="info">
              <AccordionTrigger>카드 상세 정보</AccordionTrigger>
              <AccordionContent>
                <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
                  <Row label="세트" value={auction.card?.setName ?? "-"} />
                  <Row
                    label="카드 번호"
                    value={auction.card?.cardNumber ?? "-"}
                  />
                  <Row label="언어" value={auction.card?.language ?? "-"} />
                  <Row label="희귀도" value={auction.card?.rarity ?? "-"} />
                  <Row label="인증기관" value={auction.grade?.agency ?? "-"} />
                  <Row label="감정 등급" value={auction.grade?.score ?? "-"} />
                  <Row
                    label="인증서 일련번호"
                    value={auction.grade?.serial ?? "-"}
                  />
                  <Row label="카드 상태" value={auction.cardState ?? "-"} full />
                  <Row
                    label="주요 결함"
                    value={auction.majorDefect ?? "-"}
                    full
                  />
                </dl>
              </AccordionContent>
            </AccordionItem>
          </Accordion>

          {/* CTA */}
          {auction.status !== AuctionStatus.ENDED && (
            <Button size="lg" asChild className="w-full">
              <Link
                to={
                  isLive
                    ? "/auctions/$auctionId/live"
                    : "/auctions/$auctionId/waiting"
                }
                params={{ auctionId: auction.id }}
              >
                경매 참여
              </Link>
            </Button>
          )}
        </div>
      </div>
    </PageContainer>
  );
}

function Row({
  label,
  value,
  full,
}: {
  label: string;
  value: string;
  full?: boolean;
}) {
  return (
    <div className={full ? "col-span-2" : ""}>
      <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
      <dd className="mt-0.5 text-sm text-foreground">{value}</dd>
    </div>
  );
}
