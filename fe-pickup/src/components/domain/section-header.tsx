import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** 섹션 제목 + 우측 액션(정렬 드롭다운 등) 배치 헬퍼 */
export function SectionHeader({
  title,
  description,
  action,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-end justify-between gap-4", className)}>
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold">{title}</h2>
        {description && (
          <p className="text-sm text-[var(--color-text-sub)]">{description}</p>
        )}
      </div>
      {action}
    </div>
  );
}

/** 빈 상태 */
export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-2 rounded-[var(--radius-lg)] border border-dashed border-border py-16 text-center",
        className,
      )}
    >
      <p className="text-sm font-medium text-[var(--color-text-sub)]">
        {title}
      </p>
      {description && (
        <p className="text-xs text-[var(--color-text-muted)]">{description}</p>
      )}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
