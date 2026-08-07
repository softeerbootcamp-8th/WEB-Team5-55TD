import * as React from "react";
import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import { cn } from "@/lib/utils";

function DropdownMenu({
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Root>) {
  // Radix Root의 기본값(modal=true)은 열려 있는 동안 body 스크롤을 잠그면서
  // 스크롤바 너비만큼 보정 패딩을 넣는다. 이 프로젝트는 이미 html에
  // scrollbar-gutter: stable 을 적용해 두었는데, 두 보정이 겹치면서 스크롤이
  // 있는 페이지에서 드롭다운을 열 때 레이아웃이 옆으로 밀리는 문제가 있었다.
  // 이 메뉴들은 풀스크린 모달이 아니라 가벼운 팝오버이므로 스크롤 잠금이
  // 꼭 필요하지 않아 기본값을 non-modal로 바꾼다(호출부에서 modal prop으로 재정의 가능).
  return <DropdownMenuPrimitive.Root modal={false} {...props} />;
}
const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;

function DropdownMenuContent({
  className,
  sideOffset = 8,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        sideOffset={sideOffset}
        className={cn(
          "z-50 min-w-56 overflow-hidden rounded-[var(--radius-md)] border border-border bg-[var(--color-surface-elev)] p-1.5",
          "shadow-[var(--shadow-dropdown)]",
          "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95",
          "data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
          className,
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  );
}

function DropdownMenuItem({
  className,
  inset,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Item> & {
  inset?: boolean;
}) {
  return (
    <DropdownMenuPrimitive.Item
      className={cn(
        "relative flex cursor-pointer items-center gap-2 rounded-[var(--radius-sm)] px-3 py-2 text-sm outline-none select-none",
        "focus:bg-[var(--color-surface-2)] data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
        "[&_svg]:size-4 [&_svg]:text-[var(--color-text-muted)]",
        inset && "pl-8",
        className,
      )}
      {...props}
    />
  );
}

function DropdownMenuLabel({
  className,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Label>) {
  return (
    <DropdownMenuPrimitive.Label
      className={cn(
        "px-3 py-1.5 text-xs text-[var(--color-text-muted)]",
        className,
      )}
      {...props}
    />
  );
}

function DropdownMenuSeparator({
  className,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Separator>) {
  return (
    <DropdownMenuPrimitive.Separator
      className={cn("-mx-1.5 my-1.5 h-px bg-border", className)}
      {...props}
    />
  );
}

export {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
};
