import { createFileRoute } from "@tanstack/react-router";
import { AccountSettingsPage } from "@/components/domain/account-settings-page";

export const Route = createFileRoute("/seller/settings")({
  component: AccountSettingsPage,
});
