package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.util.CursorPageSize;
import com.ootd.pickup.global.util.EpochMillis;
import java.util.List;
import java.util.function.Function;

public final class SalesCursorPaginator {

  private SalesCursorPaginator() {}

  public static <T> CursorPageResponse<T, String> paginate(
      AuctionRepository auctionRepository,
      Long sellerMemberId,
      List<AuctionStatus> statuses,
      String cursor,
      Integer size,
      Function<List<Auction>, List<T>> assembler) {
    int resolvedSize = CursorPageSize.resolve(size);
    SalesCursor decodedCursor = SalesCursor.decode(cursor);

    List<Auction> fetched =
        auctionRepository.findAllBySellerMemberIdWithCard(
            sellerMemberId, statuses, decodedCursor, resolvedSize + 1);

    boolean hasNext = fetched.size() > resolvedSize;
    List<Auction> page = hasNext ? fetched.subList(0, resolvedSize) : fetched;

    List<T> items = assembler.apply(page);

    String nextCursor = null;
    if (hasNext) {
      Auction last = page.getLast();
      nextCursor = SalesCursor.encode(EpochMillis.from(last.getEndedAt()), last.getAuctionId());
    }

    return CursorPageResponse.from(items, hasNext, nextCursor);
  }
}
