import { useEffect, useRef, useState } from "react";

const DEFAULT_DURATION_MILLIS = 450;

/** ease-out: 초반엔 빠르게, 끝에서 서서히 멈춘다. */
function easeOutQuad(t: number): number {
  return 1 - (1 - t) * (1 - t);
}

/**
 * 숫자가 바뀔 때 이전 값에서 새 값까지 부드럽게 세는 애니메이션.
 *
 * 최초 렌더에서는 애니메이션 없이 바로 표시하고(등장 애니메이션은 별도로 처리),
 * 이후 `value`가 바뀔 때만 이전 값에서부터 카운트업/다운한다.
 */
export function useAnimatedNumber(
  value: number,
  durationMs: number = DEFAULT_DURATION_MILLIS,
): number {
  const [display, setDisplay] = useState(value);
  const fromRef = useRef(value);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    const from = fromRef.current;
    if (from === value) {
      return;
    }

    const startedAt = performance.now();
    const cancel = () => {
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
    };

    const tick = () => {
      const elapsed = performance.now() - startedAt;
      const progress = Math.min(1, elapsed / durationMs);
      const eased = easeOutQuad(progress);
      setDisplay(Math.round(from + (value - from) * eased));

      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = value;
      }
    };

    rafRef.current = requestAnimationFrame(tick);
    return cancel;
  }, [value, durationMs]);

  return display;
}
