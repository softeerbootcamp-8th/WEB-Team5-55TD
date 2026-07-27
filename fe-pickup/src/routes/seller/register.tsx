import { useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ImagePlus, Search, Check } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { StepIndicator } from "@/components/domain/step-indicator";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/seller/register")({
  component: RegisterWizard,
});

const STEPS = ["카드 정보", "실물 정보", "이미지", "최종 확인"];

interface Form {
  cardName: string;
  tcg: string;
  set: string;
  number: string;
  language: string;
  rarity: string;
  agency: string;
  condition: string;
  score: string;
  serial: string;
  defects: string;
  front: boolean;
  back: boolean;
  extra: boolean;
}

const EMPTY: Form = {
  cardName: "",
  tcg: "Pokémon",
  set: "",
  number: "",
  language: "영어",
  rarity: "",
  agency: "PSA",
  condition: "",
  score: "",
  serial: "",
  defects: "",
  front: false,
  back: false,
  extra: false,
};

/** DESIGN.md · card register 1~4.html — 4단계 등록 위저드 */
function RegisterWizard() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<Form>(EMPTY);

  const set = <K extends keyof Form>(key: K, value: Form[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  // 단계별 진행 가능 조건 (DESIGN.md §6)
  const canNext =
    step === 0
      ? form.cardName.length > 0 && form.set.length > 0
      : step === 1
        ? form.serial.length > 0 && form.score.length > 0
        : step === 2
          ? form.front && form.back // 앞·뒷면 이미지 필수
          : true;

  const next = () => setStep((s) => Math.min(s + 1, STEPS.length - 1));
  const prev = () => setStep((s) => Math.max(s - 1, 0));

  return (
    <PageContainer className="flex max-w-2xl flex-col gap-8">
      <h1 className="text-2xl font-bold">카드 등록</h1>
      <StepIndicator steps={STEPS} current={step} />

      <div className="rounded-[var(--radius-lg)] border border-border bg-card p-6">
        {step === 0 && (
          <div className="flex flex-col gap-4">
            <Field label="카드명" required>
              <div className="relative">
                <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[var(--color-text-muted)]" />
                <Input
                  value={form.cardName}
                  onChange={(e) => set("cardName", e.target.value)}
                  placeholder="카드명 검색 (예: 리자몽)"
                  className="pl-10"
                />
              </div>
            </Field>
            <div className="grid grid-cols-2 gap-4">
              <Field label="TCG 종류">
                <Input
                  value={form.tcg}
                  onChange={(e) => set("tcg", e.target.value)}
                />
              </Field>
              <Field label="세트" required>
                <Input
                  value={form.set}
                  onChange={(e) => set("set", e.target.value)}
                  placeholder="Base Set"
                />
              </Field>
              <Field label="카드 번호">
                <Input
                  value={form.number}
                  onChange={(e) => set("number", e.target.value)}
                  placeholder="4/102"
                />
              </Field>
              <Field label="언어">
                <Input
                  value={form.language}
                  onChange={(e) => set("language", e.target.value)}
                />
              </Field>
              <Field label="희귀도">
                <Input
                  value={form.rarity}
                  onChange={(e) => set("rarity", e.target.value)}
                  placeholder="Holo Rare"
                />
              </Field>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="flex flex-col gap-4">
            <p className="rounded-[var(--radius-md)] bg-[var(--color-surface-2)] px-4 py-3 text-xs text-[var(--color-text-sub)]">
              인증기관(PSA · BGS · CGC) 감정을 받은 카드만 등록할 수 있습니다.
              서비스는 검수를 제공하지 않으며 인증서 일련번호로 자가 인증합니다.
            </p>
            <div className="grid grid-cols-2 gap-4">
              <Field label="인증기관">
                <Input
                  value={form.agency}
                  onChange={(e) => set("agency", e.target.value)}
                  placeholder="PSA / BGS / CGC"
                />
              </Field>
              <Field label="감정 등급" required>
                <Input
                  value={form.score}
                  onChange={(e) => set("score", e.target.value)}
                  placeholder="10"
                />
              </Field>
              <Field label="카드 상태">
                <Input
                  value={form.condition}
                  onChange={(e) => set("condition", e.target.value)}
                  placeholder="GEM MINT"
                />
              </Field>
              <Field label="인증서 일련번호" required>
                <Input
                  value={form.serial}
                  onChange={(e) => set("serial", e.target.value)}
                  placeholder="PSA-84213907"
                  className="tabular"
                />
              </Field>
            </div>
            <Field label="주요 결함 (손상 상세)">
              <Input
                value={form.defects}
                onChange={(e) => set("defects", e.target.value)}
                placeholder="예: 뒷면 우하단 미세 스크래치"
              />
            </Field>
          </div>
        )}

        {step === 2 && (
          <div className="flex flex-col gap-4">
            <p className="text-sm text-[var(--color-text-sub)]">
              앞면 · 뒷면 이미지는 필수입니다.{" "}
              <span className="text-[var(--color-danger)]">*</span>
            </p>
            <div className="grid grid-cols-3 gap-4">
              <UploadBox
                label="앞면"
                required
                done={form.front}
                onClick={() => set("front", !form.front)}
              />
              <UploadBox
                label="뒷면"
                required
                done={form.back}
                onClick={() => set("back", !form.back)}
              />
              <UploadBox
                label="추가"
                done={form.extra}
                onClick={() => set("extra", !form.extra)}
              />
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="flex flex-col gap-4">
            <h2 className="text-base font-semibold">입력 정보 최종 확인</h2>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
              <Summary label="카드명" value={form.cardName} />
              <Summary label="TCG / 세트" value={`${form.tcg} · ${form.set}`} />
              <Summary
                label="언어 / 희귀도"
                value={`${form.language} · ${form.rarity || "-"}`}
              />
              <Summary
                label="상태 / 결함"
                value={`${form.condition || "-"} · ${form.defects || "-"}`}
              />
              <Summary
                label="검증기관 / 등급"
                value={`${form.agency} ${form.score}`}
              />
              <Summary label="일련번호" value={form.serial} />
              <Summary
                label="이미지"
                value={`${[form.front, form.back, form.extra].filter(Boolean).length}장`}
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
          <Button onClick={() => navigate({ to: "/seller/products" })}>
            등록 완료
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

function UploadBox({
  label,
  required,
  done,
  onClick,
}: {
  label: string;
  required?: boolean;
  done: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex aspect-[3/4] flex-col items-center justify-center gap-2 rounded-[var(--radius-md)] border border-dashed text-xs transition-colors",
        done
          ? "border-primary bg-[var(--color-buyer-weak)] text-primary"
          : "border-border text-[var(--color-text-muted)] hover:border-[var(--color-border-strong)]",
      )}
    >
      {done ? <Check className="size-6" /> : <ImagePlus className="size-6" />}
      <span>
        {label}
        {required && <span className="text-[var(--color-danger)]"> *</span>}
      </span>
      <span className="text-[10px]">{done ? "업로드됨" : "클릭해 업로드"}</span>
    </button>
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
