import { useState } from "react";
import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { AxiosError } from "axios";
import { ArrowDownLeft, ArrowUpRight, WalletCards } from "lucide-react";
import {
  chargeMyPoint,
  getGetMyPointBalanceQueryKey,
  getGetMyPointTransactionsQueryKey,
  getMyPointTransactions,
  useGetMyPointBalance,
} from "@/api/generated/member/member";
import type { PointTransactionItemResponse } from "@/api/generated/model";
import { EmptyState } from "@/components/domain/section-header";
import { PageContainer } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { formatDateTime, formatPoint } from "@/lib/format";

export const Route = createFileRoute("/_buyer/points")({
  component: PointsPage,
});

const TRANSACTION_LABEL = {
  OPENING_BALANCE: "기존 잔액 반영",
  AUCTION_PAYMENT: "경매 낙찰 결제",
  AUCTION_PAYOUT: "경매 판매 정산",
  CHARGE: "포인트 충전",
} as const;

const MIN_CHARGE_AMOUNT = 100_000;
const MAX_CHARGE_AMOUNT = 10_000_000;
const CHARGE_PRESETS = [100_000, 300_000, 500_000, 1_000_000];

/** 백엔드가 내려주는 한글 메시지(ExceptionResponse.message)를 그대로 보여준다. */
function getChargeErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (message) return message;
  }
  return "포인트 충전에 실패했습니다. 잠시 후 다시 시도해 주세요.";
}

function PointsPage() {
  const queryClient = useQueryClient();
  const balanceQuery = useGetMyPointBalance();
  const transactionsQuery = useInfiniteQuery({
    queryKey: getGetMyPointTransactionsQueryKey(),
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

  const [amount, setAmount] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [fail, setFail] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

  const parsedAmount = (() => {
    const normalized = amount.trim().replaceAll(",", "");
    if (!/^\d+$/.test(normalized)) return null;
    const value = Number(normalized);
    return Number.isSafeInteger(value) &&
      value >= MIN_CHARGE_AMOUNT &&
      value <= MAX_CHARGE_AMOUNT
      ? value
      : null;
  })();

  const chargeMutation = useMutation({
    mutationFn: (params: { amount: number; idempotencyKey: string }) =>
      chargeMyPoint({
        amount: params.amount,
        idempotencyKey: params.idempotencyKey,
      }),
  });

  const onChargeClick = () => {
    if (parsedAmount === null) {
      setFail(
        `충전 금액은 ${formatPoint(MIN_CHARGE_AMOUNT)} 이상 ${formatPoint(MAX_CHARGE_AMOUNT)} 이하여야 합니다.`,
      );
      return;
    }
    setIdempotencyKey(crypto.randomUUID());
    setConfirmOpen(true);
  };

  const confirmCharge = () => {
    if (parsedAmount === null || idempotencyKey === null) return;
    setConfirmOpen(false);
    chargeMutation.mutate(
      { amount: parsedAmount, idempotencyKey },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetMyPointBalanceQueryKey(),
          });
          queryClient.invalidateQueries({
            queryKey: getGetMyPointTransactionsQueryKey(),
          });
          setAmount("");
          setIdempotencyKey(null);
        },
        onError: (error) => setFail(getChargeErrorMessage(error)),
      },
    );
  };

  return (
    <PageContainer className="flex flex-col gap-8">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <WalletCards className="size-7 text-primary" />
          <div>
            <h1 className="text-2xl font-bold">포인트</h1>
            <p className="text-sm text-[var(--color-text-sub)]">
              입찰에 사용할 수 있는 포인트와 실제 거래내역을 확인합니다.
            </p>
          </div>
        </div>
      </div>

      <section className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
        <h2 className="text-base font-semibold">포인트 충전</h2>
        <div className="flex gap-2">
          <Input
            inputMode="numeric"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder={`${MIN_CHARGE_AMOUNT.toLocaleString("ko-KR")} 이상`}
            className="tabular"
          />
          <Button onClick={onChargeClick} className="shrink-0">
            충전하기
          </Button>
        </div>
        <div className="flex flex-wrap gap-2" aria-label="충전 금액 프리셋">
          {CHARGE_PRESETS.map((preset) => (
            <Button
              key={preset}
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => setAmount(String(preset))}
            >
              {formatPoint(preset)}
            </Button>
          ))}
        </div>
        <p className="text-xs text-[var(--color-text-muted)]">
          실제 결제는 이루어지지 않으며, 확인 시 즉시 포인트가 적립됩니다.
        </p>
      </section>

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

      {/* 충전 확인 모달 */}
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>충전 확인</DialogTitle>
            <DialogDescription>
              실제 결제 없이 즉시 적립되는 목업 충전입니다. 금액을 확인해
              주세요.
            </DialogDescription>
          </DialogHeader>
          <dl className="flex flex-col gap-2 rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">충전 금액</dt>
              <dd className="tabular font-bold text-primary">
                {formatPoint(parsedAmount ?? 0)}
              </dd>
            </div>
          </dl>
          <DialogFooter>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => setConfirmOpen(false)}
            >
              취소
            </Button>
            <Button
              className="flex-1"
              onClick={confirmCharge}
              disabled={chargeMutation.isPending}
            >
              충전하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 충전 실패 모달 */}
      <Dialog open={fail != null} onOpenChange={(o) => !o && setFail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="text-[var(--color-danger)]">
              충전 실패
            </DialogTitle>
            <DialogDescription>{fail}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button className="w-full" onClick={() => setFail(null)}>
              확인
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
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
