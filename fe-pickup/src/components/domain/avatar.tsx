import { useState } from "react";
import { cn } from "@/lib/utils";

/** 프로필 아바타 — 이미지가 없거나 로드 실패 시 역할 액센트 연한 톤 배경에 닉네임 이니셜을 표시한다. */
export function Avatar({
  src,
  nickname,
  className,
  initialClassName = "text-2xl",
}: {
  src?: string;
  nickname: string;
  className?: string;
  /** 이니셜 글자 크기. 아바타를 작게 쓰는 곳(입찰 목록 등)에서 오버플로를 막기 위해 재정의한다. */
  initialClassName?: string;
}) {
  const [hasError, setHasError] = useState(false);
  const [prevSrc, setPrevSrc] = useState(src);

  if (src !== prevSrc) {
    setPrevSrc(src);
    setHasError(false);
  }

  const showImage = Boolean(src) && !hasError;

  return (
    <div
      className={cn(
        "flex size-24 shrink-0 items-center justify-center overflow-hidden rounded-full border border-border",
        !showImage && "bg-[var(--primary-weak)]",
        className,
      )}
    >
      {showImage ? (
        <img
          src={src}
          alt={`${nickname} 프로필 이미지`}
          className="size-full object-cover"
          onError={() => setHasError(true)}
        />
      ) : (
        <span className={cn("font-bold text-primary", initialClassName)}>
          {nickname.slice(0, 1).toUpperCase()}
        </span>
      )}
    </div>
  );
}
