import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const navigate = vi.fn();
const finishKakaoLogin = vi.fn();
const setAuthenticated = vi.fn();
const setNickname = vi.fn();
const setQueryData = vi.fn();
const updateMyProfileMutate = vi.fn();

let profileState: {
  data?: { nickname?: string };
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  refetch: () => void;
} = {
  data: { nickname: "용감한피카츄07" },
  isLoading: false,
  isError: false,
  refetch: vi.fn(),
};
let updateMutationState: { isPending: boolean } = { isPending: false };

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: unknown) => ({ options }),
  useNavigate: () => navigate,
}));
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ setQueryData }),
}));
vi.mock("@/lib/kakao-auth", () => ({ finishKakaoLogin }));
vi.mock("@/lib/auth", () => ({ setAuthenticated, setNickname }));
vi.mock("@/api/generated/member/member", () => ({
  getGetMyProfileQueryKey: () => ["getMyProfile"],
  useGetMyProfile: () => profileState,
  useUpdateMyProfile: (options: {
    mutation?: {
      onSuccess?: (value: unknown) => void;
      onError?: (error: unknown) => void;
    };
  }) => ({
    isPending: updateMutationState.isPending,
    mutate: (payload: { data: { nickname: string } }) => {
      updateMyProfileMutate(payload);
      const result = updateMyProfileMutate.mock.results.at(-1)?.value as
        | { error: unknown }
        | { success: { nickname: string } }
        | undefined;
      if (result && "error" in result) {
        options.mutation?.onError?.(result.error);
      } else if (result && "success" in result) {
        options.mutation?.onSuccess?.(result.success);
      }
    },
  }),
}));

afterEach(() => {
  vi.clearAllMocks();
  profileState = {
    data: { nickname: "용감한피카츄07" },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
  updateMutationState = { isPending: false };
});

describe("카카오 콜백 라우트", () => {
  it("code/state가 없으면 오류 문구를 표시한다", async () => {
    window.history.pushState({}, "", "/auth/kakao/callback");
    const { Route } = await import("@/routes/auth.kakao.callback");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByText("카카오 인증 응답이 올바르지 않습니다."),
    ).toBeInTheDocument();
  });

  it("최초 가입이면 닉네임 설정 화면으로 이동한다", async () => {
    window.history.pushState(
      {},
      "",
      "/auth/kakao/callback?code=abc&state=xyz",
    );
    finishKakaoLogin.mockResolvedValue({
      nickname: "용감한피카츄07",
      needsNickname: true,
    });
    const { Route } = await import("@/routes/auth.kakao.callback");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/auth/kakao/nickname",
        replace: true,
      }),
    );
    expect(setAuthenticated).toHaveBeenCalledWith(true);
  });

  it("기존 회원이면 홈으로 이동한다", async () => {
    window.history.pushState(
      {},
      "",
      "/auth/kakao/callback?code=abc&state=xyz",
    );
    finishKakaoLogin.mockResolvedValue({
      nickname: "테스터",
      needsNickname: false,
    });
    const { Route } = await import("@/routes/auth.kakao.callback");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ to: "/home", replace: true }),
    );
  });

  it("로그인 처리에 실패하면 오류 문구를 표시한다", async () => {
    window.history.pushState(
      {},
      "",
      "/auth/kakao/callback?code=abc&state=xyz",
    );
    finishKakaoLogin.mockRejectedValue(new Error("실패"));
    const { Route } = await import("@/routes/auth.kakao.callback");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    await waitFor(() =>
      expect(
        screen.getByText("카카오 로그인에 실패했습니다. 다시 시도해 주세요."),
      ).toBeInTheDocument(),
    );
  });
});

describe("카카오 닉네임 설정 라우트", () => {
  it("불러오는 중에는 안내 문구를 표시한다", async () => {
    profileState = { isLoading: true, isError: false, refetch: vi.fn() };
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByText("회원 정보를 불러오는 중입니다."),
    ).toBeInTheDocument();
  });

  it("조회에 실패하면 다시 시도 버튼을 표시한다", async () => {
    const refetch = vi.fn();
    profileState = { isLoading: false, isError: true, refetch };
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    const retry = screen.getByRole("button", { name: "다시 시도" });
    fireEvent.click(retry);
    expect(refetch).toHaveBeenCalled();
  });

  it("서버가 내려준 닉네임이 입력값 기본값으로 채워진다", async () => {
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByLabelText("닉네임")).toHaveValue("용감한피카츄07");
  });

  it("형식에 맞지 않으면 제출 버튼이 비활성화된다", async () => {
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    fireEvent.change(screen.getByLabelText("닉네임"), {
      target: { value: "a" },
    });
    expect(screen.getByRole("button", { name: "시작하기" })).toBeDisabled();
  });

  it("재생성 버튼을 누르면 입력값이 바뀐다", async () => {
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    const input = screen.getByLabelText("닉네임") as HTMLInputElement;
    const before = input.value;
    fireEvent.click(screen.getByRole("button", { name: "랜덤 닉네임 다시 생성" }));
    expect(input.value).not.toBe(before);
  });

  it("저장에 성공하면 홈으로 이동하고 닉네임을 반영한다", async () => {
    updateMyProfileMutate.mockReturnValue({
      success: { nickname: "새닉네임12" },
    });
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    fireEvent.submit(screen.getByRole("button", { name: "시작하기" }));
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ to: "/home", replace: true }),
    );
    expect(setNickname).toHaveBeenCalledWith("새닉네임12");
  });

  it("중복 등 저장 실패 시 서버 메시지를 그대로 보여준다", async () => {
    updateMyProfileMutate.mockReturnValue({
      error: {
        response: { data: { message: "이미 사용 중인 닉네임입니다." } },
      },
    });
    const { Route } = await import("@/routes/auth.kakao.nickname");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    fireEvent.submit(screen.getByRole("button", { name: "시작하기" }));
    await waitFor(() =>
      expect(
        screen.getByText("이미 사용 중인 닉네임입니다."),
      ).toBeInTheDocument(),
    );
  });
});
