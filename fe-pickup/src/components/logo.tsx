/** 모든 화면과 파비콘에서 공통으로 사용하는 PickUp 공식 로고. */
export function Logo({ className }: { className?: string }) {
  return (
    <img
      src="/logo.png"
      alt=""
      width={500}
      height={500}
      className={className}
      aria-hidden="true"
      draggable={false}
    />
  );
}
