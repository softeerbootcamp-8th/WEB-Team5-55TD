import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { toast } from "sonner";
import axios from "axios";
import { PageContainer } from "@/components/layout/page";
import {
  ConsignmentImageFields,
  type ConsignmentImageValue,
} from "@/components/domain/consignment-image-fields";
import { EmptyState } from "@/components/domain/section-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { todayDateInputValue } from "@/lib/timezone";
import { getMyConsignmentDetail } from "@/api/consignments";
import type { ConsignmentDetail } from "@/api/consignments";
import { modifyConsignment } from "@/api/generated/consignment/consignment";
import {
  CardState,
  CreateImageUploadRequestPurpose,
} from "@/api/generated/model";
import type { ExceptionResponse } from "@/api/generated/model";
import { uploadImage } from "@/api/image-upload";
import { ProductStatus } from "@/lib/types";
import { CARD_STATE_OPTIONS } from "@/lib/card-state";
import { MAJOR_DEFECT_MAX_LENGTH } from "@/lib/consignment";

export const Route = createFileRoute("/seller/products/$productId_/edit")({
  component: ProductEditPage,
});

const CERTIFICATION_BODIES = ["PSA", "BGS", "CGC", "SGC", "ACE"] as const;

const GRADES = [
  { code: "GEM_MINT", label: "GEM MINT (10)" },
  { code: "MINT", label: "MINT (9)" },
  { code: "NM_MT", label: "NM-MT (8)" },
  { code: "NM", label: "NM (7)" },
  { code: "EX_MT", label: "EX-MT (6)" },
  { code: "EX", label: "EX (5)" },
  { code: "VG_EX", label: "VG-EX (4)" },
  { code: "VG", label: "VG (3)" },
  { code: "GOOD", label: "GOOD (2)" },
  { code: "POOR", label: "POOR (1)" },
] as const;

const DEFAULT_ERROR_MESSAGE =
  "상품 정보 수정에 실패했습니다. 잠시 후 다시 시도해 주세요.";

/** 상품 정보 수정 — 카드 자체는 불변이며 감정서·이미지·주요 결함만 수정 가능 (ConsignmentStatus.isModifiable) */
function ProductEditPage() {
  const { productId } = Route.useParams();

  const {
    data: product,
    isPending,
    isError,
  } = useQuery({
    queryKey: ["consignments", "detail", productId],
    queryFn: () => getMyConsignmentDetail(productId),
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

  const canModify =
    product.status === ProductStatus.REGISTERABLE ||
    product.status === ProductStatus.REAPPLICABLE;

  if (!canModify) {
    return (
      <PageContainer className="flex flex-col gap-6">
        <EmptyState
          title="지금은 정보를 수정할 수 없는 상품이에요."
          description="경매 시작 이후 상태의 상품은 정보를 수정할 수 없습니다."
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

  return <EditForm productId={productId} product={product} />;
}

/** product가 로드된 뒤에만 마운트되므로 useState 초기값으로 안전하게 prefill 한다. */
function EditForm({
  productId,
  product,
}: {
  productId: string;
  product: ConsignmentDetail;
}) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [certificationBody, setCertificationBody] = useState<
    (typeof CERTIFICATION_BODIES)[number]
  >((product.grade?.agency as (typeof CERTIFICATION_BODIES)[number]) ?? "PSA");
  const [grade, setGrade] = useState(product.gradeCode);
  const [serialNumber, setSerialNumber] = useState(product.grade?.serial ?? "");
  const [inspectedAt, setInspectedAt] = useState(product.inspectedAt);
  const [cardState, setCardState] = useState<CardState | "">(
    product.cardState ?? "",
  );
  const [majorDefect, setMajorDefect] = useState(product.majorDefect ?? "");
  const [today] = useState(todayDateInputValue);
  const inspectedAtValid = inspectedAt.length === 0 || inspectedAt <= today;
  const [images, setImages] = useState<ConsignmentImageValue[]>(
    product.images.map((image) => ({
      kind: "existing",
      consignmentImageId: image.consignmentImageId,
      imageUrl: image.imageUrl,
    })),
  );

  const { mutate: submitModify, isPending: isSubmitting } = useMutation({
    mutationFn: async () => {
      if (!cardState) {
        throw new Error("카드 상태를 선택해 주세요.");
      }

      const imageRequests = await Promise.all(
        images.map(async (image) => {
          if (image.kind === "existing") {
            return { consignmentImageId: image.consignmentImageId };
          }

          const temporaryObjectKey = await uploadImage(
            image.file,
            CreateImageUploadRequestPurpose.CONSIGNMENT,
          );
          return { temporaryObjectKey };
        }),
      );

      return modifyConsignment(Number(productId), {
        cardState,
        majorDefect: majorDefect.trim() || undefined,
        certificate: {
          serialNumber: serialNumber.trim(),
          certificationBody,
          grade,
          inspectedAt,
        },
        images: imageRequests,
      });
    },
    onSuccess: () => {
      toast.success("상품 정보가 수정되었습니다.");
      queryClient.invalidateQueries({ queryKey: ["consignments"] });
      navigate({ to: "/seller/products/$productId", params: { productId } });
    },
    onError: (error: unknown) => {
      const message = axios.isAxiosError<ExceptionResponse>(error)
        ? error.response?.data?.message
        : error instanceof Error
          ? error.message
          : undefined;
      toast.error(message ?? DEFAULT_ERROR_MESSAGE);
    },
  });

  const canSubmit =
    grade.length > 0 &&
    cardState.length > 0 &&
    serialNumber.trim().length > 0 &&
    inspectedAt.length > 0 &&
    inspectedAtValid &&
    images.length >= 2;

  return (
    <PageContainer className="flex max-w-2xl flex-col gap-8">
      <Link
        to="/seller/products/$productId"
        params={{ productId }}
        aria-disabled={isSubmitting}
        tabIndex={isSubmitting ? -1 : undefined}
        onClick={(event) => {
          if (isSubmitting) event.preventDefault();
        }}
        className={cn(
          "inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground",
          isSubmitting && "pointer-events-none opacity-50",
        )}
      >
        <ChevronLeft className="size-4" /> 상품 상세
      </Link>

      <h1 className="text-2xl font-bold">{product.cardName} 정보 수정</h1>

      <fieldset
        disabled={isSubmitting}
        className="flex flex-col gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-6"
      >
        <p className="rounded-[var(--radius-md)] bg-[var(--color-surface-2)] px-4 py-3 text-xs text-[var(--color-text-sub)]">
          카드 자체 정보는 변경할 수 없으며, 감정서와 이미지, 카드 상태와 주요
          결함만 수정할 수 있습니다.
        </p>
        <div className="grid grid-cols-2 gap-4">
          <Field label="인증기관" required>
            <Select
              value={certificationBody}
              onChange={(e) =>
                setCertificationBody(
                  e.target.value as (typeof CERTIFICATION_BODIES)[number],
                )
              }
            >
              {CERTIFICATION_BODIES.map((body) => (
                <option key={body} value={body}>
                  {body}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="감정 등급" required>
            <Select value={grade} onChange={(e) => setGrade(e.target.value)}>
              <option value="" disabled>
                등급 선택
              </option>
              {GRADES.map((g) => (
                <option key={g.code} value={g.code}>
                  {g.label}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="인증서 일련번호" required>
            <Input
              value={serialNumber}
              onChange={(e) => setSerialNumber(e.target.value)}
              placeholder="PSA-84213907"
              className="tabular"
            />
          </Field>
          <Field label="감정일" required>
            <Input
              type="date"
              value={inspectedAt}
              max={today}
              onChange={(e) => setInspectedAt(e.target.value)}
            />
            {!inspectedAtValid && (
              <p className="text-xs text-[var(--color-danger)]">
                현재 날짜보다 이후일 수 없습니다.
              </p>
            )}
          </Field>
        </div>
        <Field label="카드 상태" required>
          <Select
            value={cardState}
            onChange={(e) => setCardState(e.target.value as CardState)}
          >
            <option value="" disabled>
              상태 선택
            </option>
            {CARD_STATE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="주요 결함 (손상 상세)">
          <Input
            value={majorDefect}
            onChange={(e) => setMajorDefect(e.target.value)}
            placeholder="예: 뒷면 우하단 미세 스크래치"
            maxLength={MAJOR_DEFECT_MAX_LENGTH}
          />
          <p className="text-right text-xs text-[var(--color-text-muted)]">
            {majorDefect.length}/{MAJOR_DEFECT_MAX_LENGTH}
          </p>
        </Field>
        <ConsignmentImageFields
          images={images}
          onChange={setImages}
          onError={(message) => toast.error(message)}
          disabled={isSubmitting}
        />
      </fieldset>

      <div className="flex justify-end gap-3">
        {isSubmitting ? (
          <Button variant="secondary" disabled>
            취소
          </Button>
        ) : (
          <Button variant="secondary" asChild>
            <Link to="/seller/products/$productId" params={{ productId }}>
              취소
            </Link>
          </Button>
        )}
        <Button
          onClick={() => submitModify()}
          disabled={!canSubmit || isSubmitting}
        >
          {isSubmitting ? "저장 중..." : "저장하기"}
        </Button>
      </div>
    </PageContainer>
  );
}

function Field({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label>
        {label}
        {required && <span className="text-[var(--color-danger)]">*</span>}
      </Label>
      {children}
    </div>
  );
}

function Select({
  className,
  children,
  ...props
}: React.ComponentProps<"select">) {
  return (
    <select
      className={cn(
        "h-11 w-full min-w-0 rounded-[var(--radius-sm)] bg-[var(--color-surface-2)] px-3.5 py-2 text-sm text-foreground",
        "outline-none transition-[box-shadow,border-color]",
        "border border-transparent",
        "focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40",
        className,
      )}
      {...props}
    >
      {children}
    </select>
  );
}
