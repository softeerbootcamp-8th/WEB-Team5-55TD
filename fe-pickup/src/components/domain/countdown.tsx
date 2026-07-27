import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { formatCountdown } from "@/lib/format";

/**
 * 카운트다운 타이머 (DESIGN.md §5.5). "HH : MM : SS", tabular.
 * 종료 임박(60초 이하) 시 danger 색으로 전환. onEnd 는 0 도달 시 1회 호출.
 */
export function Countdown({
  to,
  className,
  onEnd,
}: {
  to?: string;
  className?: string;
  onEnd?: () => void;
}) {
  const target = to ? new Date(to).getTime() : 0;
  const [left, setLeft] = useState(() => target - Date.now());
  const firedRef = useRef(false);

  useEffect(() => {
    firedRef.current = false;
    const tick = () => {
      const remaining = target - Date.now();
      setLeft(remaining);
      if (remaining <= 0 && !firedRef.current) {
        firedRef.current = true;
        onEnd?.();
      }
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [target, onEnd]);

  const urgent = left > 0 && left <= 60_000;

  return (
    <span
      className={cn(
        "tabular text-xl font-semibold tracking-wide",
        urgent ? "text-[var(--color-danger)]" : "text-foreground",
        className,
      )}
    >
      {formatCountdown(left)}
    </span>
  );
}
