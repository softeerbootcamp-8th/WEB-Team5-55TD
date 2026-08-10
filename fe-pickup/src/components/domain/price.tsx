import { cn } from "@/lib/utils";
import { formatWon, formatWonCompact } from "@/lib/format";

type PriceSize = "sm" | "md" | "lg";

const sizeClass: Record<PriceSize, string> = {
  sm: "text-base",
  md: "text-lg", // price-md (DESIGN.md §3.2)
  lg: "text-[28px] leading-9", // price-lg
};

/** 금액 표시. label(현재가/시작가/낙찰가)을 caption 으로 위에 배치 (DESIGN.md §5.3) */
export function Price({
  amount,
  label,
  size = "md",
  className,
  emphasize = true,
}: {
  amount?: number;
  label?: string;
  size?: PriceSize;
  className?: string;
  emphasize?: boolean;
}) {
  return (
    <div className={cn("flex flex-col gap-0.5", className)}>
      {label && (
        <span className="text-xs text-[var(--color-text-muted)]">{label}</span>
      )}
      <span
        title={formatWon(amount)}
        className={cn(
          "tabular font-bold",
          sizeClass[size],
          emphasize ? "text-[var(--color-price)]" : "text-foreground",
        )}
      >
        {formatWonCompact(amount)}
      </span>
    </div>
  );
}
