import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { AxiosError } from "axios";
import { ChevronLeft } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { ImageGallery } from "@/components/domain/image-gallery";
import { StatusBadge } from "@/components/domain/status-badge";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { MarketPriceChart } from "@/components/domain/market-price-chart";
import { WatchButton } from "@/components/domain/heart-button";
import { RelatedAuctionsBanner } from "@/components/domain/related-auctions-banner";
import { Avatar } from "@/components/domain/avatar";
import { Button } from "@/components/ui/button";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { getAuctionDetail } from "@/api/auctions";
import { AuctionStatus } from "@/lib/types";
import { formatDate, formatDateTime, formatWon } from "@/lib/format";
import { getCardStateLabel } from "@/lib/card-state";
import { pokemonAvatarForKey } from "@/lib/pokemon-avatars";

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
  // 개별 이미지가 없으면 썸네일 한 장으로라도 갤러리를 구성한다.
  const galleryImages =
    images.length > 0
      ? images
      : auction.thumbnailUrl
        ? [auction.thumbnailUrl]
        : [];

  return (
    <PageContainer className="flex flex-col gap-6">
      <Link
        to="/auctions"
        className="inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground"
      >
        <ChevronLeft className="size-4" /> 경매 목록
      </Link>

      <div className="grid gap-8 md:grid-cols-2">
        {/* 이미지 (DESIGN.md §5.14) */}
        <ImageGallery
          images={galleryImages}
          cardName={auction.cardName}
          grade={auction.grade}
        />

        {/* 정보 */}
        <div className="flex flex-col gap-5">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-2">
              <StatusBadge status={auction.status} />
              <GradeBadge grade={auction.grade} />
            </div>
            <WatchButton
              auctionId={auction.id}
              count={auction.watchCount}
              watched={auction.watched ?? false}
              className="bg-[var(--color-surface-2)] !text-[var(--color-text-sub)]"
            />
          </div>

          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold">
              {auction.title ?? auction.cardName}
            </h1>
            <p className="text-sm text-[var(--color-text-muted)]">
              {auction.cardName}
            </p>
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
                {auction.sellerNickname
                  ? `판매자 · ${auction.sellerNickname}`
                  : "검증된 위탁 상품"}
              </span>
            </div>
          </div>

          {auction.description && (
            <div className="text-sm whitespace-pre-wrap text-foreground">
              {auction.description}
            </div>
          )}

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
              <EndedResult won={auction.won} amount={auction.currentPrice} />
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
                  <Row
                    label="검수 완료일"
                    value={formatDate(auction.inspectedAt)}
                  />
                  <Row
                    label="카드 상태"
                    value={getCardStateLabel(auction.cardState)}
                    full
                  />
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

      <MarketPriceChart
        cardName={auction.cardName}
        setName={auction.card?.setName}
        cardNumber={auction.card?.cardNumber}
        preferredAgency={auction.grade?.agency}
        preferredScore={auction.grade?.score}
        reservePrice={auction.startPrice}
      />

      <RelatedAuctionsBanner auction={auction} />
    </PageContainer>
  );
}

/** 종료된 경매의 결과 — 낙찰이면 낙찰가를, 유찰이면 금액 대신 유찰을 보여준다. */
function EndedResult({ won, amount }: { won: boolean; amount?: number }) {
  if (won) {
    return <Price amount={amount} label="낙찰가" size="lg" />;
  }

  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-[var(--color-text-muted)]">결과</span>
      <span className="text-[28px] leading-9 font-bold text-[var(--color-text-muted)]">
        유찰
      </span>
    </div>
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
