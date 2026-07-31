import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

/** 스텝 인디케이터 (DESIGN.md §5.10). 카드 등록 4단계 등. */
export function StepIndicator({
  steps,
  current,
  className,
}: {
  steps: string[];
  current: number; // 0-based
  className?: string;
}) {
  return (
    <ol className={cn("flex items-center", className)}>
      {steps.map((label, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <li key={label} className="flex flex-1 items-center last:flex-none">
            <div className="flex items-center gap-2">
              <span
                className={cn(
                  "tabular flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold",
                  done && "bg-primary text-primary-foreground",
                  active && "border-2 border-primary text-primary",
                  !done &&
                    !active &&
                    "border border-border text-[var(--color-text-muted)]",
                )}
              >
                {done ? <Check className="size-4" /> : i + 1}
              </span>
              <span
                className={cn(
                  "text-sm whitespace-nowrap",
                  active
                    ? "font-semibold text-foreground"
                    : done
                      ? "text-[var(--color-text-sub)]"
                      : "text-[var(--color-text-muted)]",
                )}
              >
                {label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <span
                className={cn(
                  "mx-3 h-px flex-1",
                  done ? "bg-primary" : "bg-border",
                )}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}
