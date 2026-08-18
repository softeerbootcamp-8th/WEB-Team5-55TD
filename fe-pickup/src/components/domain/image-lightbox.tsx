import * as DialogPrimitive from "@radix-ui/react-dialog";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * 카드/상품 이미지를 원본 크기에 가깝게 확대해 보여주는 라이트박스.
 * 이미지가 여러 장이면 좌우 화살표 · 하단 점 인디케이터로 이동할 수 있다.
 * 열려 있는 동안만 마운트하는 방식으로 사용한다 (`{open && <ImageLightbox ... />}`).
 */
export function ImageLightbox({
  images,
  index,
  onIndexChange,
  onOpenChange,
  alt,
}: {
  images: string[];
  index: number;
  onIndexChange: (index: number) => void;
  onOpenChange: (open: boolean) => void;
  alt: string;
}) {
  const hasMultiple = images.length > 1;
  const goPrev = () =>
    onIndexChange((index - 1 + images.length) % images.length);
  const goNext = () => onIndexChange((index + 1) % images.length);

  return (
    <DialogPrimitive.Root open onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay
          className={cn(
            "fixed inset-0 z-50 bg-black/85 backdrop-blur-sm",
            "data-[state=open]:animate-in data-[state=open]:fade-in-0",
            "data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
          )}
        />
        <DialogPrimitive.Content
          onKeyDown={(e) => {
            if (!hasMultiple) return;
            if (e.key === "ArrowLeft") goPrev();
            if (e.key === "ArrowRight") goNext();
          }}
          className={cn(
            "fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 p-4 outline-none",
            "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95",
            "data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
          )}
        >
          <DialogPrimitive.Title className="sr-only">
            {alt}
          </DialogPrimitive.Title>
          <DialogPrimitive.Close
            className="absolute top-4 right-4 rounded-full bg-black/40 p-2 text-white/90 transition-colors hover:bg-black/60 focus:outline-none"
            aria-label="닫기"
          >
            <X className="size-5" />
          </DialogPrimitive.Close>

          {hasMultiple && (
            <button
              type="button"
              onClick={goPrev}
              className="absolute top-1/2 left-2 -translate-y-1/2 rounded-full bg-black/40 p-2 text-white/90 transition-colors hover:bg-black/60 md:left-6"
              aria-label="이전 이미지"
            >
              <ChevronLeft className="size-6" />
            </button>
          )}

          {/* 원본 이미지 해상도가 낮아도(카드 썸네일 등) 확대해서 자세히 볼 수 있도록,
              래퍼에 명시적으로 큰 크기를 주고 img는 그 안을 꽉 채우게 한다.
              img에 max-* 만 주면 원본 크기 이상으로는 커지지 않는다. */}
          <div className="flex h-[80vh] w-full max-w-3xl items-center justify-center">
            <img
              src={images[index]}
              alt={alt}
              className="h-full w-full rounded-[var(--radius-md)] object-contain select-none"
            />
          </div>

          {hasMultiple && (
            <button
              type="button"
              onClick={goNext}
              className="absolute top-1/2 right-2 -translate-y-1/2 rounded-full bg-black/40 p-2 text-white/90 transition-colors hover:bg-black/60 md:right-6"
              aria-label="다음 이미지"
            >
              <ChevronRight className="size-6" />
            </button>
          )}

          {hasMultiple && (
            <div className="flex gap-1.5">
              {images.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => onIndexChange(i)}
                  aria-label={`${i + 1}번째 이미지 보기`}
                  aria-current={i === index}
                  className={cn(
                    "size-1.5 rounded-full bg-white/40 transition-colors",
                    i === index && "bg-white",
                  )}
                />
              ))}
            </div>
          )}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
