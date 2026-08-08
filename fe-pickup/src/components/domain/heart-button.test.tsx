import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const navigate = vi.fn();
const toast = { info: vi.fn(), error: vi.fn() };
let authenticated = true;

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => navigate,
  useRouter: () => ({ invalidate: vi.fn() }),
}));
vi.mock("@/lib/auth", () => ({ useIsAuthenticated: () => authenticated }));
vi.mock("sonner", () => ({ toast }));
vi.mock("@/api/generated/watch/watch", () => ({
  useDeleteWatch: () => ({ isPending: false, mutate: vi.fn() }),
  useRegisterWatch: () => ({ isPending: false, mutate: vi.fn() }),
}));
vi.mock("@/api/generated/member/member", () => ({
  getGetMyWatchesQueryKey: () => ["watches"],
}));
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
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
});
