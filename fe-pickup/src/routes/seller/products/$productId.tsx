import { useState } from "react";
import {
  createFileRoute,
  Link,
  notFound,
  useNavigate,
} from "@tanstack/react-router";
import { ChevronLeft } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
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
import { products } from "@/lib/mock/data";
import { ProductStatus } from "@/lib/types";
import { PRODUCT_STATUS_META } from "@/lib/status";

export const Route = createFileRoute("/seller/products/$productId")({
  loader: ({ params }) => {
    const product = products.find((p) => p.id === params.productId);
    if (!product) throw notFound();
    return { product };
  },
  component: ProductDetailPage,
});

/** DESIGN.md · product detail.html — 정보 수정 / 경매 신청 / 삭제 */
function ProductDetailPage() {
  const { product } = Route.useLoaderData();
  const navigate = useNavigate();
  const [deleteOpen, setDeleteOpen] = useState(false);

  const meta = PRODUCT_STATUS_META[product.status];
  const canApply = product.status === ProductStatus.REGISTERABLE;
  // 경매 시작 이후 상태는 삭제 불가 (DESIGN.md §6)
  const canDelete =
    product.status === ProductStatus.REGISTERABLE ||
    product.status === ProductStatus.REAPPLICABLE;

  const { card } = product;

  return (
    <PageContainer className="flex flex-col gap-6">
      <Link
        to="/seller/products"
        className="inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground"
      >
        <ChevronLeft className="size-4" /> 상품 목록
      </Link>

      <div className="grid gap-8 md:grid-cols-2">
        <div className="flex flex-col gap-3">
          <CardThumb cardName={product.cardName} grade={product.grade} />
          <div className="grid grid-cols-4 gap-2">
            {product.images.map((img, i) => (
              <CardThumb
                key={img}
                cardName={product.cardName}
                aspect="aspect-square"
                label={i === 0 ? "앞" : "뒤"}
              />
            ))}
          </div>
        </div>

        <div className="flex flex-col gap-5">
          <div className="flex items-center gap-2">
            <Badge variant={meta.variant}>{meta.label}</Badge>
            <GradeBadge grade={product.grade} />
          </div>
          <h1 className="text-2xl font-bold">{product.cardName}</h1>

          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
            <Row label="TCG" value={card.tcg} />
            <Row label="세트" value={card.set} />
            <Row label="카드 번호" value={card.number} />
            <Row label="언어" value={card.language} />
            <Row label="희귀도" value={card.rarity} />
            <Row label="인증기관" value={product.grade.agency ?? "-"} />
            <Row label="감정 등급" value={product.grade.score ?? "-"} />
            <Row label="인증서 일련번호" value={product.grade.serial ?? "-"} />
            <Row label="카드 상태" value={card.condition} full />
            <Row label="주요 결함" value={card.defects ?? "-"} full />
          </dl>

          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" asChild>
              <Link to="/seller/register">정보 수정</Link>
            </Button>
            <Button disabled={!canApply} asChild={canApply}>
              {canApply ? (
                <Link
                  to="/seller/apply/$productId"
                  params={{ productId: product.id }}
                >
                  경매 신청
                </Link>
              ) : (
                <span>경매 신청</span>
              )}
            </Button>
            <Button
              variant="destructive"
              disabled={!canDelete}
              onClick={() => setDeleteOpen(true)}
              className="ml-auto"
            >
              삭제
            </Button>
          </div>
          {!canDelete && (
            <p className="text-xs text-[var(--color-text-muted)]">
              경매 시작 이후 상태의 상품은 삭제할 수 없습니다.
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
            >
              취소
            </Button>
            <Button
              variant="destructive"
              className="flex-1"
              onClick={() => navigate({ to: "/seller/products" })}
            >
              삭제
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
