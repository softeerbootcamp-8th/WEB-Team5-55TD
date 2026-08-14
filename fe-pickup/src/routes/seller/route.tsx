import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { Gnb } from "@/components/layout/gnb";
import { isAuthenticated } from "@/lib/auth";

export const Route = createFileRoute("/seller")({
  beforeLoad: () => {
    if (!isAuthenticated()) {
      throw redirect({ to: "/login" });
    }
  },
  component: SellerLayout,
});

/** 셀러 레이아웃 — 청록 액센트(data-role=seller) + 셀러 GNB */
function SellerLayout() {
  return (
    <div data-role="seller" className="min-h-dvh">
      <Gnb role="seller" />
      <Outlet />
    </div>
  );
}
