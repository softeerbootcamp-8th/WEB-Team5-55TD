import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

const navigate = vi.fn();
const login = vi.fn();
const createMember = vi.fn();
let healthState: {
  data?: { status: string };
  error?: Error;
  isLoading: boolean;
  isFetching: boolean;
} = {
  isLoading: true,
  isFetching: true,
};

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: unknown) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => navigate,
  redirect: (options: unknown) => options,
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: undefined,
    isPending: true,
    isError: false,
    refetch: vi.fn(),
  }),
  useMutation: (options: {
    onSuccess?: (value: unknown) => void;
    onError?: (error: unknown) => void;
  }) => ({
    isPending: false,
    mutate: (value: unknown) => {
      if (value && login.mock.results.length >= 0 && options.onSuccess)
        options.onSuccess({ nickname: "테스터" });
    },
  }),
}));
vi.mock("@/api/generated/authentication/authentication", () => ({ login }));
vi.mock("@/api/generated/member/member", () => ({
  createMember,
  useGetMyWatches: () => ({
    data: undefined,
    isPending: true,
    isError: false,
    refetch: vi.fn(),
  }),
}));
vi.mock("@/api/generated/health-check/health-check", () => ({
  useHealthCheck: () => ({ ...healthState, refetch: vi.fn() }),
}));

describe("인증 및 헬스체크 라우트", () => {
  it("로그인 폼은 입력 전 비활성이고 유효한 입력 후 활성화된다", async () => {
    const { Route } = await import("@/routes/login");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    const submit = screen.getByRole("button", { name: "로그인" });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText("아이디"), {
      target: { value: "user1" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "pass1" },
    });
    expect(submit).not.toBeDisabled();
    fireEvent.submit(screen.getByRole("button", { name: "로그인" }));
    expect(navigate).toHaveBeenCalledWith({ to: "/home" });
  });

  it("회원가입은 비밀번호 불일치를 표시하고 유효하면 로그인으로 이동한다", async () => {
    const { Route } = await import("@/routes/register");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    fireEvent.change(screen.getByLabelText(/아이디/), {
      target: { value: "user1" },
    });
    fireEvent.change(screen.getByLabelText(/닉네임/), {
      target: { value: "nick1" },
    });
    fireEvent.change(screen.getByLabelText(/^비밀번호 \*/), {
      target: { value: "pass1" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호 확인/), {
      target: { value: "other" },
    });
    expect(
      screen.getByText("비밀번호가 일치하지 않습니다."),
    ).toBeInTheDocument();
  });

  it("헬스체크 로딩·정상·오류 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/health");
    const Component = Route.options.component as React.ComponentType;
    const view = render(<Component />);
    expect(screen.getByText("로딩 중…")).toBeInTheDocument();
    healthState = {
      data: { status: "OK" },
      isLoading: false,
      isFetching: false,
    };
    view.rerender(<Component />);
    expect(screen.getByText("정상 (OK)")).toBeInTheDocument();
    healthState = {
      error: new Error("서버 오류"),
      isLoading: false,
      isFetching: false,
    };
    view.rerender(<Component />);
    expect(screen.getByText("연결 실패")).toBeInTheDocument();
  });

  it("구매자 홈·입찰·관심 목록의 초기 로딩 상태를 표시한다", async () => {
    const home = await import("@/routes/_buyer/home");
    const bids = await import("@/routes/_buyer/bids");
    const watchlist = await import("@/routes/_buyer/watchlist");
    const Home = home.Route.options.component as React.ComponentType;
    const Bids = bids.Route.options.component as React.ComponentType;
    const Watchlist = watchlist.Route.options.component as React.ComponentType;

    const homeView = render(<Home />);
    expect(screen.getByText("진행 중인 경매")).toBeInTheDocument();
    homeView.unmount();
    const bidsView = render(<Bids />);
    expect(
      screen.getByText("입찰 내역을 불러오는 중입니다."),
    ).toBeInTheDocument();
    bidsView.unmount();
    render(<Watchlist />);
    expect(
      screen.getByText("관심 경매를 불러오는 중입니다."),
    ).toBeInTheDocument();
  });
});
