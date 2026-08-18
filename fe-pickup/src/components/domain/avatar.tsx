import { useState } from "react";
import { cn } from "@/lib/utils";

/** 프로필 아바타 — 원본, 대체 이미지, 닉네임 이니셜 순서로 안전하게 폴백한다. */
export function Avatar({
  src,
  fallbackSrc,
  nickname,
  className,
  initialClassName = "text-2xl",
}: {
  src?: string;
  fallbackSrc?: string;
  nickname: string;
  className?: string;
  /** 이니셜 글자 크기. 아바타를 작게 쓰는 곳(입찰 목록 등)에서 오버플로를 막기 위해 재정의한다. */
  initialClassName?: string;
}) {
  const sources = [src, fallbackSrc].filter((candidate): candidate is string =>
    Boolean(candidate),
  );
  const sourceKey = sources.join("\n");
  const [imageState, setImageState] = useState({ sourceKey, index: 0 });

  if (sourceKey !== imageState.sourceKey) {
    setImageState({ sourceKey, index: 0 });
  }

  const activeIndex = sourceKey === imageState.sourceKey ? imageState.index : 0;
  const activeSrc = sources[activeIndex];
  const showImage = Boolean(activeSrc);

  return (
    <div
      className={cn(
        "flex size-24 shrink-0 items-center justify-center overflow-hidden rounded-full border border-border bg-[var(--primary-weak)]",
        className,
      )}
    >
      {showImage ? (
        <img
          src={activeSrc}
          alt={`${nickname} 프로필 이미지`}
          className="size-full object-cover"
          onError={() =>
            setImageState((current) => ({
              sourceKey,
              index: current.index + 1,
            }))
          }
        />
      ) : (
        <span className={cn("font-bold text-primary", initialClassName)}>
          {nickname.slice(0, 1).toUpperCase()}
        </span>
      )}
    </div>
  );
}
