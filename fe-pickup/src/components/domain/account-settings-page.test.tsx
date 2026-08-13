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
    // 닉네임은 2자부터 허용된다.
    fireEvent.change(screen.getByDisplayValue("tester"), {
      target: { value: "가" },
    });
    expect(screen.getByText("닉네임은 2~8자여야 합니다.")).toBeInTheDocument();
    fireEvent.change(screen.getByDisplayValue("가"), {
      target: { value: "가나" },
    });
    expect(
      screen.queryByText("닉네임은 2~8자여야 합니다."),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByDisplayValue("가나"), {
      target: { value: "tester2" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호"), {
      target: { value: "12345678" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
      target: { value: "5678" },
    });
    // 숫자만 8자는 두 종류 조합 규칙에 걸린다.
    expect(
      screen.getByText(
        "새 비밀번호는 영문·숫자·특수문자 중 2가지 이상 조합 8~16자여야 합니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("현재 비밀번호를 입력해 주세요."),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("새 비밀번호"), {
      target: { value: "pickup12!" },
    });
    expect(
      screen.getByText("새 비밀번호가 일치하지 않습니다."),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("현재 비밀번호"), {
      target: { value: "oldpass12!" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
      target: { value: "pickup12!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장하기" }));
    expect(updateMutate).toHaveBeenCalledWith({
      data: {
        nickname: "tester2",
        currentPassword: "oldpass12!",
        password: "pickup12!",
      },
    });
  });

  it("회원 탈퇴 버튼을 누르면 확인 모달이 열리고 '네'를 눌러야 탈퇴 요청을 보낸다", async () => {
    const { AccountSettingsPage } =
      await import("@/components/domain/account-settings-page");
    profileState = {
      isLoading: false,
      isError: false,
      data: { memberId: 1, nickname: "tester", profileImageUrl: undefined },
    };
    render(<AccountSettingsPage />);

    fireEvent.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    expect(
      screen.getByText("정말로 회원 탈퇴 하시겠습니까?"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName.toLowerCase() === "p" &&
          (element.textContent ?? "").includes(
            "많은 포켓몬들이 tester님을 기다리고 있어요",
          ),
      ),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "아니오" }));
    expect(
      screen.queryByText("정말로 회원 탈퇴 하시겠습니까?"),
    ).not.toBeInTheDocument();
    expect(withdrawMutate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    fireEvent.click(screen.getByRole("button", { name: "네" }));
    expect(withdrawMutate).toHaveBeenCalledWith();
  });

  it("카카오 가입 회원에게는 비밀번호 변경 항목을 보여주지 않는다", async () => {
    const { AccountSettingsPage } =
      await import("@/components/domain/account-settings-page");
    profileState = {
      isLoading: false,
      isError: false,
      data: {
        memberId: 1,
        nickname: "tester",
        profileImageUrl: undefined,
        oauthProvider: "KAKAO",
      },
    };
    render(<AccountSettingsPage />);

    expect(screen.queryByLabelText("현재 비밀번호")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("새 비밀번호")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("새 비밀번호 확인")).not.toBeInTheDocument();
    expect(
      screen.getByText(/카카오 계정으로 가입해 비밀번호가 없습니다/),
    ).toBeInTheDocument();
    expect(
      screen.getByText("프로필 이미지·닉네임을 변경할 수 있습니다."),
    ).toBeInTheDocument();
  });

  it("일반 회원에게는 비밀번호 변경 항목을 계속 보여준다", async () => {
    const { AccountSettingsPage } =
      await import("@/components/domain/account-settings-page");
    profileState = {
      isLoading: false,
      isError: false,
      data: { memberId: 1, nickname: "tester", profileImageUrl: undefined },
    };
    render(<AccountSettingsPage />);

    expect(screen.getByLabelText("현재 비밀번호")).toBeInTheDocument();
    expect(
      screen.getByText("프로필 이미지·닉네임·비밀번호를 변경할 수 있습니다."),
    ).toBeInTheDocument();
  });
});
