import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

let authenticated = false;
vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => vi.fn(),
}));
vi.mock("@/lib/auth", () => ({
  useIsAuthenticated: () => authenticated,
  useNickname: () => "테스터",
  setAuthenticated: vi.fn(),
}));
vi.mock("@/api/generated/authentication/authentication", () => ({
  logout: vi.fn(),
}));
vi.mock("@/api/generated/member/member", () => ({
  useGetMyProfile: () => ({
    data: { nickname: "테스터" },
    isLoading: false,
    isError: false,
  }),
  useGetMyPointBalance: () => ({
    data: {
      pointBalance: 12345,
      reservedPointBalance: 0,
      availablePointBalance: 12345,
    },
    isLoading: false,
    isError: false,
  }),
}));
vi.mock("@/components/ui/dropdown-menu", () => ({
  DropdownMenu: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownMenuContent: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownMenuTrigger: ({ children, ...props }: { children: ReactNode }) => (
    <button {...props}>{children}</button>
  ),
  DropdownMenuItem: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownMenuLabel: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownMenuSeparator: () => <hr />,
}));

describe("Gnb", () => {
  beforeEach(() => {
    authenticated = false;
  });

  it("비로그인 구매자에게 인증 링크를 표시한다", async () => {
    const { Gnb } = await import("./gnb");
    render(<Gnb role="buyer" />);
    expect(screen.getAllByText("로그인").length).toBeGreaterThan(0);
    expect(screen.getAllByText("회원가입").length).toBeGreaterThan(0);
  });

  it("로그인 셀러에게 셀러 내비게이션과 포인트를 표시한다", async () => {
    authenticated = true;
    const { Gnb } = await import("./gnb");
    render(<Gnb role="seller" />);
    expect(screen.getAllByText("PickUp 홈").length).toBeGreaterThan(0);
    expect(screen.getAllByText("상품").length).toBeGreaterThan(0);
    expect(screen.getAllByText("12,345P").length).toBeGreaterThan(0);
    expect(screen.getAllByText("테스터 님").length).toBeGreaterThan(0);
  });
});
