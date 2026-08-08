import { useState } from "react";
import type { ReactNode } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, Gavel, Pencil, Trash2 } from "lucide-react";
import { toast } from "sonner";
import type { AxiosError } from "axios";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  deleteMyConsignment,
  getMyConsignmentDetail,
} from "@/api/consignments";
import type { ExceptionResponse } from "@/api/generated/model";
import { ProductStatus } from "@/lib/types";
import { PRODUCT_STATUS_META } from "@/lib/status";

const DEFAULT_DELETE_ERROR_MESSAGE =
  "상품 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.";

export const Route = createFileRoute("/seller/products/$productId")({
  component: ProductDetailPage,
});

/** DESIGN.md · product detail.html — 정보 수정 / 경매 신청 / 삭제 */
function ProductDetailPage() {
  const { productId } = Route.useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [activeImage, setActiveImage] = useState(0);

  const {
    data: product,
    isPending,
    isError,
  } = useQuery({
    queryKey: ["consignments", "detail", productId],
    queryFn: () => getMyConsignmentDetail(productId),
  });

  const { mutate: deleteProduct, isPending: isDeleting } = useMutation({
    mutationFn: (id: string) => deleteMyConsignment(id),
    onSuccess: () => {
      toast.success("상품이 삭제되었습니다.");
      queryClient.invalidateQueries({ queryKey: ["consignments"] });
      navigate({ to: "/seller/products" });
    },
    onError: (error: AxiosError<ExceptionResponse>) => {
      toast.error(
        error.response?.data?.message ?? DEFAULT_DELETE_ERROR_MESSAGE,
      );
    },
  });

  if (isPending) return null;

  if (isError) {
    return (
      <PageContainer className="flex flex-col gap-6">
        <EmptyState
          title="상품 정보를 불러오지 못했습니다."
          description="잠시 후 다시 시도해 주세요."
        />
      </PageContainer>
    );
  }

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

  const meta = PRODUCT_STATUS_META[product.status];
  const canApply = product.status === ProductStatus.REGISTERABLE;
  const isAuctionUpcoming = product.status === ProductStatus.AUCTION_UPCOMING;
  // 경매 등록 완료 이후 상태는 수정·삭제 불가 (DESIGN.md §6, ConsignmentStatus.isModifiable)
  const canModify =
    product.status === ProductStatus.REGISTERABLE ||
    product.status === ProductStatus.REAPPLICABLE;
  const canDelete = canModify;

  // 카드 원본 이미지를 대표 사진으로 우선 노출하고, 위탁 등록 시 첨부한 실물 사진은 썸네일로 보여준다.
  const gallery = [
    product.thumbnailUrl,
    ...product.images.map((image) => image.imageUrl),
  ].filter((img): img is string => !!img);
  const mainImage = gallery[activeImage] ?? gallery[0];

  return (
    <PageContainer className="flex flex-col gap-6">
      <Link
        to="/seller/products"
        className="inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground"
      >
        <ChevronLeft className="size-4" /> 상품 목록
      </Link>

      <div className="grid gap-8 md:grid-cols-[3fr_2fr]">
        <div className="flex flex-col gap-3">
          <CardThumb
            cardName={product.cardName}
            grade={product.grade}
            imageUrl={mainImage}
            className="w-full"
          />
          {gallery.length > 1 && (
            <div className="grid grid-cols-5 gap-2">
              {gallery.map((img, i) => (
                <button
                  key={img}
                  type="button"
                  onClick={() => setActiveImage(i)}
                  aria-label={`${product.cardName} 이미지 ${i + 1}`}
                  aria-pressed={i === activeImage}
                  className="rounded-[var(--radius-md)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <CardThumb
                    cardName={product.cardName}
                    imageUrl={img}
                    aspect="aspect-square"
                    className={
                      i === activeImage
                        ? "ring-2 ring-[var(--color-text-sub)]"
                        : "opacity-70"
                    }
                  />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex flex-col gap-5">
          <div className="flex items-center gap-2">
            <GradeBadge grade={product.grade} />
          </div>
          <h1 className="text-2xl font-bold">{product.cardName}</h1>

          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
            <Row label="상태">
              <Badge variant={meta.variant}>{meta.label}</Badge>
            </Row>
            <Row label="세트" value={product.setName} />
            <Row label="카드 번호" value={product.cardNumber} />
            <Row label="언어" value={product.language} />
            <Row label="희귀도" value={product.rarity} />
            <Row label="인증기관" value={product.grade?.agency ?? "-"} />
            <Row label="감정 등급" value={product.grade?.score ?? "-"} />
            <Row label="인증서 일련번호" value={product.grade?.serial ?? "-"} />
            <Row label="주요 결함" value={product.majorDefect ?? "-"} full />
          </dl>

          <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
            {canModify && (
              <Button
                variant="secondary"
                size="lg"
                asChild
                className="w-full shrink-0 sm:w-auto"
              >
                <Link
                  to="/seller/products/$productId/edit"
                  params={{ productId: product.id }}
                >
                  <Pencil className="size-4 shrink-0" /> 정보 수정
                </Link>
              </Button>
            )}
            {canApply && (
              <Button size="lg" asChild className="w-full shrink-0 sm:w-auto">
                <Link
                  to="/seller/apply/$productId"
                  params={{ productId: product.id }}
                >
                  <Gavel className="size-4 shrink-0" /> 경매 신청
                </Link>
              </Button>
            )}
            {canDelete && (
              <Button
                variant="destructive"
                size="lg"
                onClick={() => setDeleteOpen(true)}
                className="w-full shrink-0 sm:w-auto"
              >
                <Trash2 className="size-4 shrink-0" /> 삭제
              </Button>
            )}
          </div>
          {isAuctionUpcoming && (
            <p className="text-xs text-[var(--color-text-muted)]">
              경매 예정 상태의 상품은 정보 수정·경매 취소가 불가합니다.
            </p>
          )}
          {!isAuctionUpcoming && !canModify && (
            <p className="text-xs text-[var(--color-text-muted)]">
              경매 시작 이후 상태의 상품은 정보를 수정할 수 없습니다.
            </p>
          )}
        </div>
      </div>

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>상품을 삭제할까요?</DialogTitle>
            <DialogDescription>
              삭제한 상품은 복구할 수 없습니다.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => setDeleteOpen(false)}
              disabled={isDeleting}
            >
              취소
            </Button>
            <Button
              variant="destructive"
              className="flex-1"
              onClick={() => deleteProduct(product.id)}
              disabled={isDeleting}
            >
              {isDeleting ? "삭제 중..." : "삭제"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}

function Row({
  label,
  value,
  full,
  children,
}: {
  label: string;
  value?: string;
  full?: boolean;
  children?: ReactNode;
}) {
  return (
    <div className={full ? "col-span-2" : ""}>
      <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
      <dd className="mt-0.5 text-sm text-foreground">{children ?? value}</dd>
    </div>
  );
}
