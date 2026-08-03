import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { Grade } from "@/lib/types";

/** 감정 등급 배지 — 중립 배경 + 강조 텍스트 (DESIGN.md §5.4, 예: PSA 10) */
export function GradeBadge({
  grade,
  className,
}: {
  grade?: Grade;
  className?: string;
}) {
  if (!grade?.agency) return null;
  return (
    <Badge
      variant="neutral"
      className={cn("self-start font-semibold text-foreground", className)}
    >
      {grade.agency} {grade.score}
    </Badge>
  );
}
