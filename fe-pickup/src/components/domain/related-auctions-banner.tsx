import { useQuery } from "@tanstack/react-query";
import { EmptyState, SectionHeader } from "@/components/domain/section-header";
import { AuctionCard } from "@/components/domain/auction-card";
import { searchAuctions, type AuctionDetailView } from "@/api/auctions";
import type { AuctionSummary } from "@/lib/types";

const RELATED_STATUS = ["SCHEDULED", "ONGOING"] as const;
const RELATED_SIZE = 6;

/** 상세 화면 하단 · 같은 판매자의 다른 경매 + 같은 카드의 다른 경매 (DESIGN.md §7 auction detail) */
export function RelatedAuctionsBanner({
  auction,
}: {
  auction: AuctionDetailView;
}) {
  const sellerId = auction.sellerId ? Number(auction.sellerId) : undefined;
  const cardId = auction.card?.cardId;
  const excludeAuctionId = Number(auction.id);

  const sellerQuery = useQuery({
    queryKey: ["auctions", "seller-other", sellerId, excludeAuctionId],
    queryFn: () =>
      searchAuctions({
        status: [...RELATED_STATUS],
        sort: "ENDING_SOON",
        size: RELATED_SIZE,
        sellerId,
        excludeAuctionId,
      }),
    enabled: sellerId != null,
  });

  const similarQuery = useQuery({
    queryKey: ["auctions", "similar-card", cardId, excludeAuctionId],
    queryFn: () =>
      searchAuctions({
        status: [...RELATED_STATUS],
        sort: "ENDING_SOON",
        size: RELATED_SIZE,
        cardId,
        excludeAuctionId,
      }),
    enabled: cardId != null,
  });

  const sellerAuctions = sellerQuery.data?.items ?? [];
  const similarAuctions = similarQuery.data?.items ?? [];
  // 조회할 카드가 없으면 쿼리가 비활성이라 계속 pending 이다. 이때는 결과 없음으로 본다.
  const isSimilarLoading = cardId != null && similarQuery.isPending;

  return (
    <div className="flex flex-col gap-10">
      {sellerAuctions.length > 0 && (
        <section className="flex flex-col gap-5">
          <SectionHeader
            title="같은 판매자의 다른 경매"
            description={
              auction.sellerNickname
                ? `${auction.sellerNickname}님이 등록한 다른 경매입니다.`
                : undefined
            }
          />
          <AuctionGrid auctions={sellerAuctions} />
        </section>
      )}

      <section className="flex flex-col gap-5">
        <SectionHeader
          title="같은 카드의 다른 경매"
          description={`${auction.cardName}을(를) 판매하는 다른 경매입니다.`}
        />
        {similarAuctions.length > 0 && (
          <AuctionGrid auctions={similarAuctions} />
        )}
        {similarAuctions.length === 0 && !isSimilarLoading && (
          <EmptyState
            title="같은 카드의 다른 경매가 없습니다."
            description="지금은 이 경매에서만 만나볼 수 있어요."
          />
        )}
      </section>
    </div>
  );
}

function AuctionGrid({ auctions }: { auctions: AuctionSummary[] }) {
  return (
    <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
      {auctions.map((item) => (
        <AuctionCard key={item.id} auction={item} />
      ))}
    </div>
  );
}
