import { createLucideIcon, Radio } from "lucide-react";
import { cn } from "@/lib/utils";
import type { RealtimeConnectionStatus } from "@/hooks/use-auction-bid-updates";

// lucide 에 radio-off 가 없어 radio 글리프에 슬래시를 얹어 만든다. 앞의 다섯 요소는
// lucide radio 와 같고, "m2 2 20 20" 은 lucide 의 off 변형(wifi-off 등)이 쓰는 슬래시다.
const RadioOff = createLucideIcon("radio-off", [
  ["path", { d: "M16.247 7.761a6 6 0 0 1 0 8.478", key: "1fwjs5" }],
  ["path", { d: "M19.075 4.933a10 10 0 0 1 0 14.134", key: "ehdyv1" }],
  ["path", { d: "M4.925 19.067a10 10 0 0 1 0-14.134", key: "1q22gi" }],
  ["path", { d: "M7.753 16.239a6 6 0 0 1 0-8.478", key: "r2q7qm" }],
  ["circle", { cx: "12", cy: "12", r: "2", key: "1c9p78" }],
  ["path", { d: "m2 2 20 20", key: "1ooewy" }],
]);

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
    Icon: RadioOff,
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
