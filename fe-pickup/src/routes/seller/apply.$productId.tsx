import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { toast } from "sonner";
import type { AxiosError } from "axios";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { registerAuction } from "@/api/auctions";
import { getMyConsignmentDetail } from "@/api/consignments";
import type { ExceptionResponse } from "@/api/generated/model";
import { ProductStatus } from "@/lib/types";
import {
  formatDateTime,
  formatWon,
  minBidUnit,
  MINIMUM_STARTING_PRICE,
} from "@/lib/format";
import {
  kstLocalInputToUtcIso,
  utcInstantToKstLocalInput,
} from "@/lib/timezone";

export const Route = createFileRoute("/seller/apply/$productId")({
  component: AuctionApplyPage,
});

const DEFAULT_ERROR_MESSAGE =
  "경매 신청에 실패했습니다. 잠시 후 다시 시도해 주세요.";

function parsePositivePrice(value: string): number | null {
  const normalized = value.trim().replaceAll(",", "");
  if (!/^\d+$/.test(normalized)) return null;
  const parsed = Number(normalized);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

/** DESIGN.md · auction-apply.html — 희망 시작가/낙찰가/일정, 신청 후 수정·삭제 불가 */
function AuctionApplyPage() {
  const { productId } = Route.useParams();
  const navigate = useNavigate();

  const { data: product, isPending } = useQuery({
    queryKey: ["consignments", "detail", productId],
    queryFn: () => getMyConsignmentDetail(productId),
  });

  const [startPrice, setStartPrice] = useState("");
  const [reserve, setReserve] = useState("");
  const [schedule, setSchedule] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [minimumSchedule] = useState(() => {
    // 요청이 서버에 도착하는 사이에 과거 시각이 되지 않도록 1분의 여유를 둔다.
    return utcInstantToKstLocalInput(Date.now() + 60_000);
  });

  const startValue = parsePositivePrice(startPrice);
  const reserveValue = parsePositivePrice(reserve);
  const startPriceTooLow =
    startValue !== null && startValue < MINIMUM_STARTING_PRICE;
  const reserveBelowStart =
    startValue !== null && reserveValue !== null && startValue > reserveValue;
  const priceRangeValid =
    startValue !== null &&
    !startPriceTooLow &&
    reserveValue !== null &&
    !reserveBelowStart;
  const unit = startValue ? minBidUnit(startValue) : 0;
  const scheduleValue = schedule
    ? Date.parse(kstLocalInputToUtcIso(schedule))
    : Number.NaN;
  const minimumScheduleValue = Date.parse(
    kstLocalInputToUtcIso(minimumSchedule),
  );
  const scheduleValid =
    Number.isFinite(scheduleValue) && scheduleValue > minimumScheduleValue;
  const valid = priceRangeValid && scheduleValid && title.trim().length > 0;

  const { mutate: submitApply, isPending: isSubmitting } = useMutation({
    mutationFn: () =>
      registerAuction({
        consignmentId: productId,
        startingPrice: startValue!,
        reserve: reserveValue!,
        scheduledStartAt: kstLocalInputToUtcIso(schedule),
        title,
        description,
      }),
    onSuccess: () => {
      toast.success("경매 신청이 완료되었습니다.");
      navigate({ to: "/seller/products/$productId", params: { productId } });
    },
    onError: (error: AxiosError<ExceptionResponse>) => {
      setConfirmOpen(false);
      toast.error(error.response?.data?.message ?? DEFAULT_ERROR_MESSAGE);
    },
  });

  if (isPending) return null;

  if (!product) {
    return (
      <PageContainer className="flex flex-col gap-6">
        <EmptyState
          title="상품을 찾을 수 없습니다."
          action={
            <Button variant="secondary" asChild>
              <Link to="/seller/products">상품 목록으로</Link>
            </Button>
          }
        />
      </PageContainer>
    );
  }

  if (
    product.status !== ProductStatus.REGISTERABLE &&
    product.status !== ProductStatus.REAPPLICABLE
  ) {
    return (
      <PageContainer className="flex flex-col gap-6">
        <EmptyState
          title="지금은 경매를 신청할 수 없는 상품이에요."
          description="등록 가능 상태의 상품만 경매를 신청할 수 있습니다."
          action={
            <Button variant="secondary" asChild>
              <Link to="/seller/products/$productId" params={{ productId }}>
                상품 상세로
              </Link>
            </Button>
          }
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="flex max-w-2xl flex-col gap-6">
      <Link
        to="/seller/products/$productId"
        params={{ productId }}
        className="inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground"
      >
        <ChevronLeft className="size-4" /> 상품 상세
      </Link>

      <h1 className="text-2xl font-bold">경매 신청</h1>

      {/* 대상 상품 */}
      <div className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4">
        <CardThumb
          cardName={product.cardName}
          imageUrl={product.thumbnailUrl}
          aspect="aspect-square"
          className="w-16"
        />
        <div className="flex flex-col gap-1">
          <GradeBadge grade={product.grade} />
          <span className="text-sm font-semibold">{product.cardName}</span>
          <span className="tabular text-xs text-[var(--color-text-muted)]">
            인증서 {product.grade?.serial ?? "-"}
          </span>
        </div>
      </div>

      <div className="flex flex-col gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-6">
        <div className="flex flex-col gap-1.5">
          <Label>
            경매 제목 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="경매 제목을 입력해 주세요"
            maxLength={100}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label>경매 본문 (선택)</Label>
          <Input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="경매 본문을 입력해 주세요"
            maxLength={1000}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label>
            희망 시작가 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            inputMode="numeric"
            value={startPrice}
            onChange={(e) => setStartPrice(e.target.value)}
            placeholder="1,000,000"
            className="tabular"
          />
          {startPrice && startValue === null && (
            <p className="text-xs text-[var(--color-danger)]">
              시작가는 0보다 큰 안전한 범위의 정수로 입력해 주세요.
            </p>
          )}
          {startPriceTooLow && (
            <p className="text-xs text-[var(--color-danger)]">
              시작가는 {formatWon(MINIMUM_STARTING_PRICE)} 이상으로 입력해
              주세요.
            </p>
          )}
          <p className="text-xs text-[var(--color-text-muted)]">
            최소 입찰 단위는 시작가의 5%로 시스템이 결정합니다 —{" "}
            <span className="tabular font-semibold text-foreground">
              {formatWon(unit)}
            </span>
          </p>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label>
            최소 희망 낙찰가 (비공개){" "}
            <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            inputMode="numeric"
            value={reserve}
            onChange={(e) => setReserve(e.target.value)}
            placeholder="구매자에게 공개되지 않습니다"
            className="tabular"
          />
          {reserve && reserveValue === null && (
            <p className="text-xs text-[var(--color-danger)]">
              최소 희망 낙찰가는 0보다 큰 안전한 범위의 정수로 입력해 주세요.
            </p>
          )}
          {reserveBelowStart && (
            <p className="text-xs text-[var(--color-danger)]">
              최소 희망 낙찰가는 희망 시작가 이상으로 입력해 주세요.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label>
            희망 시작 일시 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            type="datetime-local"
            value={schedule}
            onChange={(e) => setSchedule(e.target.value)}
            min={minimumSchedule}
          />
          {schedule && !scheduleValid && (
            <p className="text-xs text-[var(--color-danger)]">
              현재보다 이후의 일시를 선택해 주세요.
            </p>
          )}
          <p className="text-xs text-[var(--color-text-muted)]">
            선택한 일시에 시작하며 7일간 진행됩니다.
          </p>
        </div>
      </div>

      <p className="rounded-[var(--radius-md)] bg-[var(--color-surface-2)] px-4 py-3 text-xs text-[var(--color-text-sub)]">
        신청 후에는 수정·삭제할 수 없습니다. 종료 5분 내 입찰이 발생하면 종료
        시각이 5분 연장되며, 유찰 시 재신청이 가능합니다.
      </p>

      <Button
        size="lg"
        disabled={!valid}
        onClick={() => setConfirmOpen(true)}
        className="w-full"
      >
        경매 신청
      </Button>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>경매를 신청할까요?</DialogTitle>
            <DialogDescription>
              신청 후에는 수정·삭제할 수 없습니다.
            </DialogDescription>
          </DialogHeader>
          <dl className="flex flex-col gap-2 rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">희망 시작가</dt>
              <dd className="tabular font-semibold">
                {formatWon(startValue ?? 0)}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">경매 일정</dt>
              <dd className="tabular text-right">
                {schedule
                  ? `${formatDateTime(kstLocalInputToUtcIso(schedule))}부터 7일`
                  : "-"}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">최소 입찰 단위</dt>
              <dd className="tabular">{formatWon(unit)}</dd>
            </div>
          </dl>
          <DialogFooter>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => setConfirmOpen(false)}
              disabled={isSubmitting}
            >
              취소
            </Button>
            <Button
              className="flex-1"
              onClick={() => submitApply()}
              disabled={isSubmitting}
            >
              {isSubmitting ? "신청 중..." : "신청 확정"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
