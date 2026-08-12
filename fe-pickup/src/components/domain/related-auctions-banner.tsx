import { useQuery } from "@tanstack/react-query";
import { SectionHeader } from "@/components/domain/section-header";
import { AuctionCard } from "@/components/domain/auction-card";
import { searchAuctions, type AuctionDetailView } from "@/api/auctions";

const RELATED_STATUS = ["SCHEDULED", "ONGOING"] as const;
const RELATED_SIZE = 6;

/** 상세 화면 하단 · 같은 판매자의 다른 경매 + 비슷한 카드 경매 (DESIGN.md 미기재 · OOTD-437) */
export function RelatedAuctionsBanner({ auction }: { auction: AuctionDetailView }) {
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

  if (sellerAuctions.length === 0 && similarAuctions.length === 0) {
    return null;
  }

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
          <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
            {sellerAuctions.map((item) => (
              <AuctionCard key={item.id} auction={item} />
            ))}
          </div>
        </section>
      )}

      {similarAuctions.length > 0 && (
        <section className="flex flex-col gap-5">
          <SectionHeader
            title="비슷한 카드 경매"
            description={`${auction.cardName}의 다른 경매입니다.`}
          />
          <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
            {similarAuctions.map((item) => (
              <AuctionCard key={item.id} auction={item} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
