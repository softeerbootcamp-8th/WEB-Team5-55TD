import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { toast } from "sonner";
import type { AxiosError } from "axios";
import { PageContainer } from "@/components/layout/page";
import { StepIndicator } from "@/components/domain/step-indicator";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { useSearchCards } from "@/api/generated/card/card";
import { registerConsignment } from "@/api/generated/consignment/consignment";
import type {
  ExceptionResponse,
  RegisterConsignmentRequest,
  SearchCardsResponse,
} from "@/api/generated/model";

export const Route = createFileRoute("/seller/register")({
  component: RegisterWizard,
});

const STEPS = ["카드 정보", "실물 정보", "이미지", "최종 확인"];

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
  "상품 등록에 실패했습니다. 잠시 후 다시 시도해 주세요.";

/** DESIGN.md · card register 1~4.html — 4단계 등록 위저드 */
function RegisterWizard() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);

  // 1단계 — 카드 검색 및 선택
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [selectedCard, setSelectedCard] = useState<SearchCardsResponse | null>(
    null,
  );

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedKeyword(keyword.trim()), 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  const { data: searchData, isFetching: isSearching } = useSearchCards(
    { keyword: debouncedKeyword, size: 8 },
    { query: { enabled: debouncedKeyword.length > 0 } },
  );
  const searchResults = searchData?.items ?? [];

  const resetCardSelection = () => {
    setSelectedCard(null);
    setKeyword("");
    setDebouncedKeyword("");
  };

  // 2단계 — 감정서(실물) 정보
  const [certificationBody, setCertificationBody] =
    useState<(typeof CERTIFICATION_BODIES)[number]>("PSA");
  const [grade, setGrade] = useState("");
  const [serialNumber, setSerialNumber] = useState("");
  const [inspectedAt, setInspectedAt] = useState("");
  const [majorDefect, setMajorDefect] = useState("");

  // 3단계 — 이미지 URL (업로드 API 미구현 — URL 직접 입력)
  const [frontImageUrl, setFrontImageUrl] = useState("");
  const [backImageUrl, setBackImageUrl] = useState("");
  const [extraImageUrl, setExtraImageUrl] = useState("");

  const canNext =
    step === 0
      ? selectedCard !== null
      : step === 1
        ? grade.length > 0 && serialNumber.trim().length > 0 && inspectedAt.length > 0
        : step === 2
          ? frontImageUrl.trim().length > 0 && backImageUrl.trim().length > 0
          : true;

  const next = () => setStep((s) => Math.min(s + 1, STEPS.length - 1));
  const prev = () => setStep((s) => Math.max(s - 1, 0));

  const { mutate: submitRegister, isPending: isSubmitting } = useMutation({
    mutationFn: (payload: RegisterConsignmentRequest) =>
      registerConsignment(payload),
    onSuccess: () => {
      toast.success("상품이 등록되었습니다.");
      navigate({ to: "/seller/products" });
    },
    onError: (error: AxiosError<ExceptionResponse>) => {
      toast.error(error.response?.data?.message ?? DEFAULT_ERROR_MESSAGE);
    },
  });

  const handleSubmit = () => {
    if (!selectedCard?.cardId || isSubmitting) return;
    submitRegister({
      cardId: selectedCard.cardId,
      majorDefect: majorDefect.trim() || undefined,
      certificate: {
        serialNumber: serialNumber.trim(),
        certificationBody,
        grade,
        inspectedAt,
      },
      images: [
        { imageUrl: frontImageUrl.trim() },
        { imageUrl: backImageUrl.trim() },
        ...(extraImageUrl.trim() ? [{ imageUrl: extraImageUrl.trim() }] : []),
      ],
    });
  };

  return (
    <PageContainer className="flex max-w-2xl flex-col gap-8">
      <h1 className="text-2xl font-bold">카드 등록</h1>
      <StepIndicator steps={STEPS} current={step} />

      <div className="rounded-[var(--radius-lg)] border border-border bg-card p-6">
        {step === 0 && (
          <div className="flex flex-col gap-4">
            {!selectedCard ? (
              <>
                <Field label="카드명 검색" required>
                  <div className="relative">
                    <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[var(--color-text-muted)]" />
                    <Input
                      value={keyword}
                      onChange={(e) => setKeyword(e.target.value)}
                      placeholder="카드명 검색 (예: 리자몽)"
                      className="pl-10"
                    />
                  </div>
                </Field>
                {debouncedKeyword.length > 0 && (
                  <div className="flex flex-col gap-2">
                    {isSearching && (
                      <p className="text-xs text-[var(--color-text-muted)]">
                        검색 중...
                      </p>
                    )}
                    {!isSearching && searchResults.length === 0 && (
                      <p className="text-xs text-[var(--color-text-muted)]">
                        검색 결과가 없습니다.
                      </p>
                    )}
                    {searchResults.map((card) => (
                      <button
                        key={card.cardId}
                        type="button"
                        onClick={() => setSelectedCard(card)}
                        className="flex items-center gap-3 rounded-[var(--radius-md)] border border-border p-3 text-left transition-colors hover:border-primary"
                      >
                        {card.imageUrl && (
                          <img
                            src={card.imageUrl}
                            alt={card.cardName}
                            className="size-12 rounded-[var(--radius-sm)] object-cover"
                          />
                        )}
                        <div className="flex flex-col">
                          <span className="text-sm font-semibold">
                            {card.cardName}
                          </span>
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {card.setName} · {card.cardNumber} ·{" "}
                            {card.language} · {card.rarity}
                          </span>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div className="flex items-center justify-between gap-3 rounded-[var(--radius-md)] border border-primary bg-[var(--primary-weak)] p-4">
                <div className="flex items-center gap-3">
                  {selectedCard.imageUrl && (
                    <img
                      src={selectedCard.imageUrl}
                      alt={selectedCard.cardName}
                      className="size-14 rounded-[var(--radius-sm)] object-cover"
                    />
                  )}
                  <div className="flex flex-col">
                    <span className="text-sm font-semibold">
                      {selectedCard.cardName}
                    </span>
                    <span className="text-xs text-[var(--color-text-sub)]">
                      {selectedCard.setName} · {selectedCard.cardNumber} ·{" "}
                      {selectedCard.language} · {selectedCard.rarity}
                    </span>
                  </div>
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={resetCardSelection}
                >
                  다시 선택
                </Button>
              </div>
            )}
          </div>
        )}

        {step === 1 && (
          <div className="flex flex-col gap-4">
            <p className="rounded-[var(--radius-md)] bg-[var(--color-surface-2)] px-4 py-3 text-xs text-[var(--color-text-sub)]">
              인증기관(PSA · BGS · CGC · SGC · ACE) 감정을 받은 카드만 등록할
              수 있습니다. 서비스는 검수를 제공하지 않으며 인증서 일련번호로
              자가 인증합니다.
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
                  onChange={(e) => setInspectedAt(e.target.value)}
                />
              </Field>
            </div>
            <Field label="주요 결함 (손상 상세)">
              <Input
                value={majorDefect}
                onChange={(e) => setMajorDefect(e.target.value)}
                placeholder="예: 뒷면 우하단 미세 스크래치"
              />
            </Field>
          </div>
        )}

        {step === 2 && (
          <div className="flex flex-col gap-4">
            <p className="text-sm text-[var(--color-text-sub)]">
              앞면 · 뒷면 이미지 URL은 필수입니다.{" "}
              <span className="text-[var(--color-danger)]">*</span>{" "}
              (이미지 업로드 기능은 아직 준비 중이라 URL을 직접 입력합니다.)
            </p>
            <Field label="앞면 이미지 URL" required>
              <Input
                value={frontImageUrl}
                onChange={(e) => setFrontImageUrl(e.target.value)}
                placeholder="https://example.com/cards/front.png"
              />
            </Field>
            <Field label="뒷면 이미지 URL" required>
              <Input
                value={backImageUrl}
                onChange={(e) => setBackImageUrl(e.target.value)}
                placeholder="https://example.com/cards/back.png"
              />
            </Field>
            <Field label="추가 이미지 URL">
              <Input
                value={extraImageUrl}
                onChange={(e) => setExtraImageUrl(e.target.value)}
                placeholder="https://example.com/cards/extra.png"
              />
            </Field>
          </div>
        )}

        {step === 3 && (
          <div className="flex flex-col gap-4">
            <h2 className="text-base font-semibold">입력 정보 최종 확인</h2>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
              <Summary label="카드명" value={selectedCard?.cardName ?? "-"} />
              <Summary
                label="세트 / 번호"
                value={`${selectedCard?.setName ?? "-"} · ${selectedCard?.cardNumber ?? "-"}`}
              />
              <Summary
                label="언어 / 희귀도"
                value={`${selectedCard?.language ?? "-"} · ${selectedCard?.rarity ?? "-"}`}
              />
              <Summary
                label="인증기관 / 등급"
                value={`${certificationBody} · ${GRADES.find((g) => g.code === grade)?.label ?? "-"}`}
              />
              <Summary label="일련번호" value={serialNumber} />
              <Summary label="감정일" value={inspectedAt} />
              <Summary label="주요 결함" value={majorDefect || "-"} />
              <Summary
                label="이미지"
                value={`${[frontImageUrl, backImageUrl, extraImageUrl].filter((v) => v.trim().length > 0).length}장`}
              />
            </dl>
          </div>
        )}
      </div>

      {/* 네비게이션 */}
      <div className="flex justify-between">
        <Button variant="secondary" onClick={prev} disabled={step === 0}>
          이전
        </Button>
        {step < STEPS.length - 1 ? (
          <Button onClick={next} disabled={!canNext}>
            다음 단계
          </Button>
        ) : (
          <Button onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? "등록 중..." : "등록 완료"}
          </Button>
        )}
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

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
      <dd className="mt-0.5 text-sm text-foreground">{value || "-"}</dd>
    </div>
  );
}
