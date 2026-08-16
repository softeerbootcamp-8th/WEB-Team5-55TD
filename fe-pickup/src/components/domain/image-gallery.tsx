import { useState } from "react";
import { Expand } from "lucide-react";
import { CardThumb } from "@/components/domain/card-thumb";
import { ImageLightbox } from "@/components/domain/image-lightbox";
import type { Grade } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * 상세 화면 이미지 갤러리 (DESIGN.md §5.14).
 *
 * 대표 사진을 누르면 확대되고, 아래 썸네일을 누르면 대표가 그 사진으로 바뀐다.
 * 썸네일 줄은 전체 이미지를 순서 그대로 두고 현재 것만 강조한다 — 누를 때마다
 * 줄이 재구성되면 눈으로 쫓기 어렵기 때문이다.
 */
export function ImageGallery({
  images,
  cardName,
  grade,
  className,
}: {
  images: string[];
  cardName: string;
  grade?: Grade;
  className?: string;
}) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isLightboxOpen, setIsLightboxOpen] = useState(false);

  const hasImages = images.length > 0;
  // 이미지 목록이 줄어드는 경우에도 범위를 벗어나지 않게 한다.
  const safeIndex = Math.min(activeIndex, Math.max(0, images.length - 1));

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <button
        type="button"
        onClick={() => hasImages && setIsLightboxOpen(true)}
        disabled={!hasImages}
        aria-label={hasImages ? `${cardName} 이미지 확대` : undefined}
        className="group relative block w-full rounded-[var(--radius-md)] text-left disabled:cursor-default"
      >
        <CardThumb
          cardName={cardName}
          grade={grade}
          imageUrl={images[safeIndex]}
          className="w-full"
        />
        {hasImages && (
          <span className="pointer-events-none absolute inset-0 flex items-center justify-center rounded-[var(--radius-md)] opacity-0 transition-opacity group-hover:bg-black/20 group-hover:opacity-100">
            <Expand className="size-6 text-white drop-shadow" />
          </span>
        )}
      </button>

      {images.length > 1 && (
        <div className="grid grid-cols-5 gap-2">
          {images.map((image, index) => (
            <button
              key={`${image}-${index}`}
              type="button"
              onClick={() => setActiveIndex(index)}
              aria-label={`${cardName} 이미지 ${index + 1}`}
              aria-pressed={index === safeIndex}
              className="rounded-[var(--radius-md)] focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            >
              <CardThumb
                cardName={cardName}
                imageUrl={image}
                aspect="aspect-square"
                className={
                  index === safeIndex
                    ? "ring-2 ring-[var(--color-text-sub)]"
                    : "opacity-70"
                }
              />
            </button>
          ))}
        </div>
      )}

      {isLightboxOpen && (
        <ImageLightbox
          images={images}
          index={safeIndex}
          onIndexChange={setActiveIndex}
          onOpenChange={(open) => !open && setIsLightboxOpen(false)}
          alt={cardName}
        />
      )}
    </div>
  );
}
