import { Radio, WifiOff } from "lucide-react";
import { cn } from "@/lib/utils";
import type { RealtimeConnectionStatus } from "@/hooks/use-auction-bid-updates";

const STATUS_VIEWS = {
  connected: {
    Icon: Radio,
    label: "실시간",
    className: "text-[var(--color-success)]",
  },
  reconnecting: {
    Icon: Radio,
    label: "재연결 중",
    className: "animate-pulse text-[var(--color-warning)]",
  },
  disconnected: {
    Icon: WifiOff,
    label: "연결 끊김",
    className: "text-[var(--color-danger)]",
  },
} as const;

/** 웹소켓 연결 상태 라벨 (DESIGN.md §5.11) */
export function ConnectionStatus({
  status,
  className,
}: {
  status: RealtimeConnectionStatus;
  className?: string;
}) {
  const { Icon, label, className: statusClassName } = STATUS_VIEWS[status];

  return (
    <span
      role="status"
      className={cn(
        "inline-flex items-center gap-1 text-xs",
        statusClassName,
        className,
      )}
    >
      <Icon className="size-3.5" /> {label}
    </span>
  );
}
