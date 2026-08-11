import { useInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowDownLeft, ArrowUpRight, WalletCards } from "lucide-react";
import {
  getMyPointTransactions,
  useGetMyPointBalance,
} from "@/api/generated/member/member";
import type { PointTransactionItemResponse } from "@/api/generated/model";
import { EmptyState } from "@/components/domain/section-header";
import { PageContainer } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { formatDateTime, formatPoint } from "@/lib/format";

export const Route = createFileRoute("/_buyer/points")({
  component: PointsPage,
});

const TRANSACTION_LABEL = {
  OPENING_BALANCE: "기존 잔액 반영",
  AUCTION_PAYMENT: "경매 낙찰 결제",
  AUCTION_PAYOUT: "경매 판매 정산",
} as const;

function PointsPage() {
  const balanceQuery = useGetMyPointBalance();
  const transactionsQuery = useInfiniteQuery({
    queryKey: ["my-point-transactions"],
    queryFn: ({ pageParam }) =>
      getMyPointTransactions({
        getPointTransactionsRequest: { cursor: pageParam, size: 20 },
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.cursor : undefined,
  });

  const transactions =
    transactionsQuery.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <PageContainer className="flex flex-col gap-8">
      <div className="flex items-center gap-3">
        <WalletCards className="size-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">포인트</h1>
          <p className="text-sm text-[var(--color-text-sub)]">
            입찰에 사용할 수 있는 포인트와 실제 거래내역을 확인합니다.
          </p>
        </div>
      </div>

      {balanceQuery.isPending ? (
        <p className="py-8 text-center text-sm text-[var(--color-text-sub)]">
          포인트를 불러오는 중입니다.
        </p>
      ) : balanceQuery.isError || !balanceQuery.data ? (
        <EmptyState
          title="포인트를 불러오지 못했습니다."
          description="잠시 후 다시 시도해 주세요."
          action={
            <Button variant="secondary" onClick={() => balanceQuery.refetch()}>
              다시 시도
            </Button>
          }
        />
      ) : (
        <section className="grid gap-3 md:grid-cols-3">
          <BalanceCard
            label="사용 가능 포인트"
            value={balanceQuery.data.availablePointBalance}
            accent
          />
          <BalanceCard
            label="총 보유 포인트"
            value={balanceQuery.data.pointBalance}
          />
          <BalanceCard
            label="입찰 예약 포인트"
            value={balanceQuery.data.reservedPointBalance}
          />
        </section>
      )}

      <section className="flex flex-col gap-4">
        <div>
          <h2 className="text-lg font-semibold">거래내역</h2>
          <p className="text-sm text-[var(--color-text-sub)]">
            예약과 해제는 제외하고 실제로 증감한 내역만 표시합니다.
          </p>
        </div>

        {transactionsQuery.isPending ? (
          <p className="py-12 text-center text-sm text-[var(--color-text-sub)]">
            거래내역을 불러오는 중입니다.
          </p>
        ) : transactionsQuery.isError ? (
          <EmptyState
            title="거래내역을 불러오지 못했습니다."
            action={
              <Button
                variant="secondary"
                onClick={() => transactionsQuery.refetch()}
              >
                다시 시도
              </Button>
            }
          />
        ) : transactions.length === 0 ? (
          <EmptyState title="포인트 거래내역이 없습니다." />
        ) : (
          <>
            <ul className="divide-y divide-border overflow-hidden rounded-[var(--radius-lg)] border border-border bg-card">
              {transactions.map((transaction) => (
                <TransactionRow
                  key={transaction.pointTransactionId}
                  transaction={transaction}
                />
              ))}
            </ul>
            {transactionsQuery.hasNextPage && (
              <Button
                variant="secondary"
                disabled={transactionsQuery.isFetchingNextPage}
                onClick={() => transactionsQuery.fetchNextPage()}
              >
                {transactionsQuery.isFetchingNextPage
                  ? "불러오는 중"
                  : "거래내역 더 보기"}
              </Button>
            )}
          </>
        )}
      </section>
    </PageContainer>
  );
}

function BalanceCard({
  label,
  value,
  accent = false,
}: {
  label: string;
  value: number;
  accent?: boolean;
}) {
  return (
    <div className="rounded-[var(--radius-lg)] border border-border bg-card p-5">
      <p className="text-sm text-[var(--color-text-sub)]">{label}</p>
      <p
        className={`mt-2 text-2xl font-bold tabular-nums ${accent ? "text-primary" : "text-foreground"}`}
      >
        {formatPoint(value)}
      </p>
    </div>
  );
}

function TransactionRow({
  transaction,
}: {
  transaction: PointTransactionItemResponse;
}) {
  const positive = transaction.amount > 0;
  const Icon = positive ? ArrowDownLeft : ArrowUpRight;
  const content = (
    <div className="flex items-center justify-between gap-4 px-4 py-4">
      <div className="flex min-w-0 items-center gap-3">
        <span
          className={`flex size-9 shrink-0 items-center justify-center rounded-full ${positive ? "bg-[color-mix(in_srgb,var(--color-success)_18%,transparent)] text-[var(--color-success)]" : "bg-[color-mix(in_srgb,var(--color-danger)_18%,transparent)] text-[var(--color-danger)]"}`}
        >
          <Icon className="size-4" />
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">
            {TRANSACTION_LABEL[transaction.transactionType]}
          </p>
          <p className="text-xs text-[var(--color-text-muted)]">
            {formatDateTime(transaction.createdAt)}
          </p>
        </div>
      </div>
      <div className="shrink-0 text-right">
        <p
          className={`font-semibold tabular-nums ${positive ? "text-[var(--color-success)]" : "text-foreground"}`}
        >
          {positive ? "+" : "-"}
          {formatPoint(Math.abs(transaction.amount))}
        </p>
        <p className="text-xs text-[var(--color-text-muted)]">
          잔액 {formatPoint(transaction.balanceAfter)}
        </p>
      </div>
    </div>
  );

  return (
    <li>
      {transaction.auctionId ? (
        <Link
          to="/auctions/$auctionId"
          params={{ auctionId: String(transaction.auctionId) }}
          className="block hover:bg-[var(--color-surface-2)]"
        >
          {content}
        </Link>
      ) : (
        content
      )}
    </li>
  );
}
