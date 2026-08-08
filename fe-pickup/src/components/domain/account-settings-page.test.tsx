import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

let profileState: {
  data?: unknown;
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
} = {
  isLoading: true,
  isError: false,
};
const refetch = vi.fn();

vi.mock("@/api/generated/member/member", () => ({
  getGetMyProfileQueryKey: () => ["profile"],
  useGetMyProfile: () => ({ ...profileState, refetch }),
  useUpdateMyProfile: () => ({ isPending: false, mutate: vi.fn() }),
}));
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ setQueryData: vi.fn() }),
}));
vi.mock("@/api/image-upload", () => ({
  IMAGE_ACCEPT: "image/*",
  getImageValidationError: () => null,
  uploadImage: vi.fn(),
}));

describe("AccountSettingsPage", () => {
  it("loading, error, empty profile 상태를 표시한다", async () => {
    const { AccountSettingsPage } =
      await import("@/components/domain/account-settings-page");
    const view = render(<AccountSettingsPage />);
    expect(
      screen.getByText("계정 정보를 불러오는 중입니다."),
    ).toBeInTheDocument();

    profileState = {
      isLoading: false,
      isError: true,
      error: new Error("failed"),
    };
    view.rerender(<AccountSettingsPage />);
    expect(
      screen.getByText("계정 정보를 불러오지 못했습니다."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(refetch).toHaveBeenCalled();

    profileState = { isLoading: false, isError: false, data: {} };
    view.rerender(<AccountSettingsPage />);
    expect(
      screen.getByText("표시할 계정 정보가 없습니다."),
    ).toBeInTheDocument();
  });

  it("프로필이 있으면 계정 설정 폼을 표시한다", async () => {
    const { AccountSettingsPage } =
      await import("@/components/domain/account-settings-page");
    profileState = {
      isLoading: false,
      isError: false,
      data: { memberId: 1, nickname: "tester", profileImageUrl: undefined },
    };
    render(<AccountSettingsPage />);
    expect(
      screen.getByRole("button", { name: /이미지 변경/ }),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue("tester")).toBeInTheDocument();
    expect(screen.getByLabelText("새 비밀번호")).toBeInTheDocument();
  });
});
