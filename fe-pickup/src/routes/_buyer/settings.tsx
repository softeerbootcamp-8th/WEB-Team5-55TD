import { createFileRoute, redirect } from "@tanstack/react-router";
import { AccountSettingsPage } from "@/components/domain/account-settings-page";
import { isAuthenticated } from "@/lib/auth";

export const Route = createFileRoute("/_buyer/settings")({
  beforeLoad: () => {
    if (!isAuthenticated()) {
      throw redirect({ to: "/login" });
    }
  },
  component: AccountSettingsPage,
});
