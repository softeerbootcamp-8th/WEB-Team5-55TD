import { cn } from "@/lib/utils";

/** 프로필 아바타 — 이미지가 없으면 역할 액센트(구매자=핑크·셀러=퍼플)의 연한 톤 배경에 닉네임 이니셜을 표시한다. */
export function Avatar({
  src,
  nickname,
  className,
}: {
  src?: string;
  nickname: string;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex size-24 shrink-0 items-center justify-center overflow-hidden rounded-full border border-border",
        !src && "bg-[var(--primary-weak)]",
        className,
      )}
    >
      {src ? (
        <img
          src={src}
          alt={`${nickname} 프로필 이미지`}
          className="size-full object-cover"
        />
      ) : (
        <span className="text-2xl font-bold text-primary">
          {nickname.slice(0, 1).toUpperCase()}
        </span>
      )}
    </div>
  );
}
