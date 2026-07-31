import { cn } from "@/lib/utils";
import type { Grade } from "@/lib/types";

/**
 * 카드 썸네일 플레이스홀더.
 * 실제 이미지가 없는 목 데이터 환경용 — 카드명 해시로 그라데이션을 만들어
 * 카드별로 구분되는 시각적 표면을 제공한다. 실제 이미지 연동 시 <img> 로 교체.
 */
function hashHue(text: string): number {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) % 360;
  return h;
}

export function CardThumb({
  cardName,
  grade,
  imageUrl,
  label,
  className,
  aspect = "aspect-[3/4]",
}: {
  cardName: string;
  grade?: Grade;
  imageUrl?: string;
  label?: string;
  className?: string;
  aspect?: string;
}) {
  const hue = hashHue(cardName);
  return (
    <div
      className={cn(
        "relative flex items-center justify-center overflow-hidden rounded-[var(--radius-md)] border border-border",
        aspect,
        className,
      )}
      style={{
        background: `linear-gradient(150deg, hsl(${hue} 45% 22%), hsl(${(hue + 40) % 360} 50% 12%))`,
      }}
    >
      {imageUrl && (
        <img
          src={imageUrl}
          alt={cardName}
          className="absolute inset-0 size-full object-cover"
        />
      )}
      <div className="flex flex-col items-center gap-1 px-3 text-center">
        <span
          className={cn(
            "text-sm font-semibold text-white/90 [text-wrap:balance]",
            imageUrl && "sr-only",
          )}
        >
          {cardName}
        </span>
        {grade?.agency && (
          <span className="tabular text-xs text-white/60">
            {grade.agency} {grade.score}
          </span>
        )}
        {label && (
          <span className="mt-1 rounded-[var(--radius-pill)] bg-black/40 px-2 py-0.5 text-[10px] tracking-widest text-white/70 uppercase">
            {label}
          </span>
        )}
      </div>
    </div>
  );
}
