import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const navigate = vi.fn();
const toast = { info: vi.fn(), error: vi.fn() };
let authenticated = true;
const deleteMutate = vi.fn();
const registerMutate = vi.fn();
const invalidateQueries = vi.fn();
const setQueriesData = vi.fn();
const invalidateRouter = vi.fn();

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => navigate,
  useRouter: () => ({ invalidate: invalidateRouter }),
}));
vi.mock("@/lib/auth", () => ({ useIsAuthenticated: () => authenticated }));
vi.mock("sonner", () => ({ toast }));
vi.mock("@/api/generated/watch/watch", () => ({
  useDeleteWatch: () => ({ isPending: false, mutate: deleteMutate }),
  useRegisterWatch: () => ({ isPending: false, mutate: registerMutate }),
}));
vi.mock("@/api/generated/member/member", () => ({
  getGetMyWatchesQueryKey: () => ["watches"],
}));
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries, setQueriesData }),
}));

describe("HeartButton", () => {
  beforeEach(() => {
    authenticated = true;
    vi.clearAllMocks();
  });

  it("인증된 사용자는 관심 상태를 토글한다", async () => {
    const { HeartButton } = await import("./heart-button");
    const onToggle = vi.fn();
    render(<HeartButton count={3} onToggle={onToggle} />);
    const button = screen.getByRole("button", { name: "관심 등록" });
    expect(screen.getByText("3")).toBeInTheDocument();
    fireEvent.click(button);
    expect(onToggle).toHaveBeenCalledWith(true);
  });

  it("비로그인 사용자는 로그인 안내를 본다", async () => {
    authenticated = false;
    const { HeartButton } = await import("./heart-button");
    render(<HeartButton />);
    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    expect(toast.info).toHaveBeenCalledWith(
      "로그인 후 관심 경매를 등록할 수 있습니다.",
      expect.objectContaining({ action: expect.any(Object) }),
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it("WatchButton은 관심 등록·해제 요청을 낙관적으로 처리한다", async () => {
    const { WatchButton } = await import("./heart-button");
    render(<WatchButton auctionId="7" watched={false} count={2} />);
    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    expect(registerMutate).toHaveBeenCalledWith(
      { auctionId: 7 },
      expect.objectContaining({
        onError: expect.any(Function),
        onSettled: expect.any(Function),
      }),
    );
    const options = registerMutate.mock.calls[0][1];
    await options.onSettled();
    expect(invalidateQueries).toHaveBeenCalled();
    expect(invalidateRouter).toHaveBeenCalled();

    cleanup();
    render(<WatchButton auctionId="7" watched count={2} />);
    fireEvent.click(screen.getByRole("button", { name: "관심 해제" }));
    expect(deleteMutate).toHaveBeenCalled();
  });

  it("빠른 연속 클릭으로 관심 등록이 중복돼도 활성 상태를 유지한다", async () => {
    const { WatchButton } = await import("./heart-button");
    render(<WatchButton auctionId="7" watched={false} count={2} />);

    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    await act(async () => {
      await registerMutate.mock.calls[0][1].onSettled();
    });

    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    act(() => {
      registerMutate.mock.calls[1][1].onError({
        response: {
          status: 409,
          data: { message: "이미 관심 등록한 경매입니다." },
        },
      });
    });

    expect(toast.error).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "관심 해제" }),
    ).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("관심 등록이 실패하면 낙관적 상태를 롤백하고 오류를 알린다", async () => {
    const { WatchButton } = await import("./heart-button");
    render(<WatchButton auctionId="7" watched={false} count={2} />);

    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    act(() => {
      registerMutate.mock.calls[0][1].onError({
        response: { status: 500, data: { message: "서버 오류" } },
      });
    });

    expect(toast.error).toHaveBeenCalledWith("서버 오류");
    expect(
      screen.getByRole("button", { name: "관심 등록" }),
    ).toBeInTheDocument();
  });
});

describe("관심 토글과 경매 목록 정렬", () => {
  beforeEach(() => {
    authenticated = true;
    vi.clearAllMocks();
  });

  it("경매 목록은 즉시 다시 불러오지 않는다", async () => {
    const { WatchButton } = await import("./heart-button");
    render(<WatchButton auctionId="7" watched={false} count={2} />);

    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    await registerMutate.mock.calls[0][1].onSettled();

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ["auctions"],
      refetchType: "none",
    });
    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: ["auctions"],
    });
  });

  it("경매 목록 캐시의 관심 상태를 직접 갈아끼운다", async () => {
    const { WatchButton } = await import("./heart-button");
    render(<WatchButton auctionId="7" watched={false} count={2} />);

    fireEvent.click(screen.getByRole("button", { name: "관심 등록" }));
    await registerMutate.mock.calls[0][1].onSettled();

    const [filters, updater] = setQueriesData.mock.calls[0];
    expect(filters).toEqual({ queryKey: ["auctions"] });
    expect(
      updater({ items: [{ id: "7", watched: false, watchCount: 2 }] }),
    ).toEqual({
      items: [{ id: "7", watched: true, watchCount: 3 }],
    });
  });
});
