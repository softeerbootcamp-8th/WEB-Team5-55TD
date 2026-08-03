import { createFileRoute, redirect } from "@tanstack/react-router";
import { AccountSettingsPage } from "@/components/domain/account-settings-page";
import { isAuthenticated } from "@/lib/auth";

export const Route = createFileRoute("/_buyer/settings")({
  beforeLoad: ({ location }) => {
    if (!isAuthenticated()) {
      throw redirect({ to: "/login", search: { redirect: location.href } });
    }
  },
  component: AccountSettingsPage,
});
