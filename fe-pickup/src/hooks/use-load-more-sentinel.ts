import { useEffect, useRef } from "react";

/**
 * 스크롤이 하단 sentinel에 닿으면 `onIntersect`를 호출하는 무한 스크롤 트리거.
 *
 * jsdom(테스트 환경)에는 `IntersectionObserver`가 없으므로, 없으면 그냥 아무 일도
 * 하지 않는다 — 자동 트리거만 못 할 뿐, 호출부에서 항상 남겨두는 수동 "더 보기"
 * 버튼이 대체 경로가 된다.
 */
export function useLoadMoreSentinel({
  enabled,
  onIntersect,
  root,
}: {
  enabled: boolean;
  onIntersect: () => void;
  root?: Element | null;
}) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const onIntersectRef = useRef(onIntersect);

  useEffect(() => {
    onIntersectRef.current = onIntersect;
  }, [onIntersect]);

  useEffect(() => {
    if (!enabled) return;
    const target = sentinelRef.current;
    if (!target || typeof IntersectionObserver === "undefined") return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          onIntersectRef.current();
        }
      },
      { root, rootMargin: "200px" },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [enabled, root]);

  return sentinelRef;
}
