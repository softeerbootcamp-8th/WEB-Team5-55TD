import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

const { searchAuctionsMock } = vi.hoisted(() => ({
  searchAuctionsMock: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryFn }: { queryFn: () => unknown }) => {
    queryFn();
    return {
      data: undefined,
      isPending: true,
      isError: false,
      refetch: vi.fn(),
    };
  },
}));
vi.mock("@/api/auctions", () => ({ searchAuctions: searchAuctionsMock }));

const SORT_CASES = [
  { label: "인기순", apiSort: "POPULAR" },
  { label: "가격 낮은순", apiSort: "PRICE_ASC" },
  { label: "가격 높은순", apiSort: "PRICE_DESC" },
  { label: "종료 임박순", apiSort: "ENDING_SOON" },
  { label: "시작 임박순", apiSort: "STARTING_SOON" },
  { label: "최신순", apiSort: "RECENT" },
] as const;

beforeEach(() => {
  searchAuctionsMock.mockClear();
});

describe("경매 목록 라우트", () => {
  it("검색·정렬·필터 UI와 로딩 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/index");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "경매" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("카드명으로 검색")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "진행 중" })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole("button", { name: /인기순/ }), {
      key: "Enter",
    });
    for (const { label } of SORT_CASES) {
      expect(screen.getByRole("menuitem", { name: label })).toBeInTheDocument();
    }
    expect(screen.getByText("경매를 불러오는 중입니다.")).toBeInTheDocument();
  });

  it.each(SORT_CASES)(
    "$label 선택 시 $apiSort 정렬로 조회한다",
    async ({ label, apiSort }) => {
      const { Route } = await import("@/routes/_buyer/auctions/index");
      const Component = Route.options.component as React.ComponentType;
      render(<Component />);

      if (label !== "인기순") {
        const trigger = screen.getByRole("button", { name: /인기순/ });
        fireEvent.keyDown(trigger, { key: "Enter" });
        fireEvent.click(await screen.findByRole("menuitem", { name: label }));
      }

      await waitFor(() =>
        expect(searchAuctionsMock).toHaveBeenLastCalledWith({
          q: undefined,
          status: ["ONGOING"],
          sort: apiSort,
          size: 100,
        }),
      );
    },
  );
});
