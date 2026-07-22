import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** 콘텐츠 영역 컨테이너 — 최대 폭 중앙 정렬 (DESIGN.md §4.2) */
export function PageContainer({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "mx-auto w-full max-w-[var(--container-max)] px-8 py-8",
        className,
      )}
    >
      {children}
    </div>
  );
}
