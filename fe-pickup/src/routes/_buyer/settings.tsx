import { createFileRoute } from "@tanstack/react-router";
import { AccountSettingsPage } from "@/components/domain/account-settings-page";

export const Route = createFileRoute("/_buyer/settings")({
  component: AccountSettingsPage,
});
