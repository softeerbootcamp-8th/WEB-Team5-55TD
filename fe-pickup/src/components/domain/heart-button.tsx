import { useState } from "react";
import { Heart } from "lucide-react";
import { cn } from "@/lib/utils";

/** 관심(하트) 버튼 (DESIGN.md §5.3). 카드 우상단 배치용. */
export function HeartButton({
  defaultActive = false,
  count,
  className,
  onToggle,
}: {
  defaultActive?: boolean;
  count?: number;
  className?: string;
  onToggle?: (active: boolean) => void;
}) {
  const [active, setActive] = useState(defaultActive);

  return (
    <button
      type="button"
      aria-pressed={active}
      aria-label={active ? "관심 해제" : "관심 등록"}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        const next = !active;
        setActive(next);
        onToggle?.(next);
      }}
      className={cn(
        "inline-flex items-center gap-1 rounded-[var(--radius-pill)] bg-black/40 px-2 py-1 text-xs backdrop-blur-sm transition-colors",
        active
          ? "text-primary"
          : "text-white/80 hover:text-white",
        className,
      )}
    >
      <Heart className={cn("size-4", active && "fill-current")} />
      {count != null && <span className="tabular">{count}</span>}
    </button>
  );
}
