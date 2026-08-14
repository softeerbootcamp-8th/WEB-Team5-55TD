import { ImageOff } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";
import type { Grade } from "@/lib/types";

export function CardThumb({
  cardName,
  grade,
  imageUrl,
  label,
  className,
  aspect = "aspect-[3/4]",
  shineOnGroupHover = false,
}: {
  cardName: string;
  grade?: Grade;
  imageUrl?: string;
  label?: string;
  className?: string;
  aspect?: string;
  shineOnGroupHover?: boolean;
}) {
  const [loaded, setLoaded] = useState(false);
  const [loadedForUrl, setLoadedForUrl] = useState(imageUrl);
  if (imageUrl !== loadedForUrl) {
    setLoadedForUrl(imageUrl);
    setLoaded(false);
  }
  const showImage = Boolean(imageUrl) && loaded;

  return (
    <div
      className={cn(
        // 이미지가 없거나 로딩 전/실패 상태, 그리고 투명 배경 PNG 뒤로 비치는
        // 색 모두 이 배경이다 — 카드마다 색을 다르게 주던 이전 방식(해시 기반
        // 그라데이션)은 톤에 따라 누리끼리하게 보이는 경우가 있어, 이 앱의
        // 다른 서페이스(입력창·리스트 셀 등)와 동일한 중성 회색 한 가지로
        // 통일한다(globals.css --color-surface-2).
        "relative flex items-center justify-center overflow-hidden rounded-[var(--radius-md)] border border-border bg-[var(--color-surface-2)]",
        aspect,
        className,
      )}
    >
      {imageUrl && (
        <img
          src={imageUrl}
          alt={cardName}
          className={cn(
            "absolute inset-0 size-full object-cover",
            !loaded && "hidden",
          )}
          onLoad={() => setLoaded(true)}
          onError={() => setLoaded(false)}
        />
      )}
      {shineOnGroupHover && showImage && (
        <span aria-hidden className="auction-card-image-shine" />
      )}
      {/* 등급·라벨은 아래(GradeBadge)에서 별도로도 보여주므로, 이미지가 뜨면
          실제 사진 위에 겹쳐 보일 필요 없이 플레이스홀더에서만 노출한다. */}
      <div className="flex flex-col items-center gap-1 px-3 text-center">
        <ImageOff
          aria-hidden
          className={cn(
            "mb-1 size-6 text-[var(--color-text-muted)]",
            showImage && "hidden",
          )}
        />
        <span
          className={cn(
            "text-sm font-semibold text-[var(--color-text-sub)] [text-wrap:balance]",
            showImage && "sr-only",
          )}
        >
          {cardName}
        </span>
        {grade?.agency && (
          <span
            className={cn(
              "tabular text-xs text-[var(--color-text-muted)]",
              showImage && "sr-only",
            )}
          >
            {grade.agency} {grade.score}
          </span>
        )}
        {label && (
          <span
            className={cn(
              "mt-1 rounded-[var(--radius-pill)] bg-[var(--color-card)] px-2 py-0.5 text-[10px] tracking-widest text-[var(--color-text-sub)] uppercase",
              showImage && "sr-only",
            )}
          >
            {label}
          </span>
        )}
      </div>
    </div>
  );
}
