import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { AuctionStatus } from "@/lib/types";

/** 경매 상태 배지 (DESIGN.md §5.4) */
export function StatusBadge({
  status,
  className,
}: {
  status: AuctionStatus;
  className?: string;
}) {
  if (status === AuctionStatus.LIVE) {
    return (
      <Badge variant="live" className={className}>
        <span className="size-1.5 animate-[var(--animate-live-pulse)] rounded-full bg-[var(--color-live)]" />
        진행 중
      </Badge>
    );
  }
  if (status === AuctionStatus.UPCOMING) {
    return (
      <Badge variant="warning" className={className}>
        예정
      </Badge>
    );
  }
  return (
    <Badge variant="muted" className={className}>
      종료
    </Badge>
  );
}

/** 낙찰 결과 배지 */
export function ResultBadge({
  won,
  className,
}: {
  won: boolean;
  className?: string;
}) {
  return won ? (
    <Badge variant="success" className={className}>
      낙찰
    </Badge>
  ) : (
    <Badge variant="muted" className={cn(className)}>
      유찰
    </Badge>
  );
}
