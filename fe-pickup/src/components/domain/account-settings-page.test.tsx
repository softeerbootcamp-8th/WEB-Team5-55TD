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
const updateMutate = vi.fn();
const withdrawMutate = vi.fn();
const navigate = vi.fn();

vi.mock("@/api/generated/member/member", () => ({
  getGetMyProfileQueryKey: () => ["profile"],
  useGetMyProfile: () => ({ ...profileState, refetch }),
  useUpdateMyProfile: () => ({ isPending: false, mutate: updateMutate }),
}));
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ setQueryData: vi.fn() }),
  useMutation: () => ({ mutate: withdrawMutate, isPending: false }),
}));
vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => navigate,
}));
vi.mock("@/api/image-upload", () => ({
  IMAGE_ACCEPT: "image/*",
  getImageValidationError: () => null,
  uploadImage: vi.fn(),
}));
vi.mock("@/api/member", () => ({
  withdrawMember: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

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
    fireEvent.change(screen.getByDisplayValue("tester"), {
      target: { value: "abc" },
    });
    expect(
      screen.getByText("닉네임은 4자 이상이어야 합니다."),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByDisplayValue("abc"), {
      target: { value: "tester2" },
    });
    fireEvent.change(screen.getByLabelText("현재 비밀번호"), {
      target: { value: "12" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호"), {
      target: { value: "1234" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
      target: { value: "5678" },
    });
    expect(
      screen.getByText("현재 비밀번호는 4자 이상 입력해 주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("새 비밀번호가 일치하지 않습니다."),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("현재 비밀번호"), {
      target: { value: "1234" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
      target: { value: "1234" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장하기" }));
    expect(updateMutate).toHaveBeenCalledWith({
      data: { nickname: "tester2", currentPassword: "1234", password: "1234" },
    });
  });

  it("회원 탈퇴 버튼을 누르면 비밀번호 확인 모달이 열리고 탈퇴 요청을 보낸다", async () => {
    const { AccountSettingsPage } =
      await import("@/components/domain/account-settings-page");
    profileState = {
      isLoading: false,
      isError: false,
      data: { memberId: 1, nickname: "tester", profileImageUrl: undefined },
    };
    render(<AccountSettingsPage />);

    fireEvent.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    expect(screen.getByText("회원 탈퇴 하시겠어요?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "탈퇴하기" })).toBeDisabled();

    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "pw1234" },
    });
    fireEvent.click(screen.getByRole("button", { name: "탈퇴하기" }));
    expect(withdrawMutate).toHaveBeenCalledWith("pw1234");

    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(
      screen.queryByText("회원 탈퇴 하시겠어요?"),
    ).not.toBeInTheDocument();
  });
});
