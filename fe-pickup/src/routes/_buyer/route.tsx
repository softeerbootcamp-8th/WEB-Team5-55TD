import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Gnb } from "@/components/layout/gnb";

export const Route = createFileRoute("/_buyer")({
  component: BuyerLayout,
});

/** 구매자 레이아웃 — 파랑 액센트(data-role=buyer) + 구매자 GNB */
function BuyerLayout() {
  return (
    <div data-role="buyer" className="min-h-dvh">
      <Gnb role="buyer" />
      <Outlet />
    </div>
  );
}
