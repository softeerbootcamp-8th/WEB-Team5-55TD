import { fireEvent, render, screen, cleanup } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let query: Record<string, unknown> = { isPending: true };
const mutate = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useParams: () => ({ productId: "8" }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => vi.fn(),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => query,
  useMutation: () => ({ mutate, isPending: false }),
}));
vi.mock("@/api/consignments", () => ({ getMyConsignmentDetail: vi.fn() }));
vi.mock("@/api/auctions", () => ({ registerAuction: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const product = {
  id: "8",
  cardName: "Mewtwo",
  status: "REGISTERABLE",
  thumbnailUrl: "card.jpg",
  grade: { agency: "PSA", score: "10", serial: "A" },
};
describe("셀러 경매 신청", () => {
  beforeEach(() => {
    query = { isPending: true };
    vi.clearAllMocks();
  });
  it("상품 없음·신청 불가 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/seller/apply.$productId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(document.body).toBeTruthy();
    cleanup();
    query = { isPending: false, data: undefined };
    render(<Component />);
    expect(screen.getByText("상품을 찾을 수 없습니다.")).toBeInTheDocument();
    cleanup();
    query = { isPending: false, data: { ...product, status: "AUCTION_LIVE" } };
    render(<Component />);
    expect(
      screen.getByText("지금은 경매를 신청할 수 없는 상품이에요."),
    ).toBeInTheDocument();
  });
  it("가격·일정을 입력하고 신청 확인을 연다", async () => {
    query = { isPending: false, data: product };
    const { Route } = await import("@/routes/seller/apply.$productId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    fireEvent.change(screen.getByPlaceholderText("1,000,000"), {
      target: { value: "10000" },
    });
    fireEvent.change(
      screen.getByPlaceholderText("구매자에게 공개되지 않습니다"),
      { target: { value: "15000" } },
    );
    fireEvent.change(
      document.querySelector(
        'input[type="datetime-local"]',
      ) as HTMLInputElement,
      { target: { value: "2099-01-01T10:00" } },
    );
    expect(
      screen.getByText("최소 입찰 단위는 시작가의 5%로 시스템이 결정합니다 —"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "경매 신청" }));
    expect(screen.getByText("경매를 신청할까요?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByText("경매를 신청할까요?")).not.toBeInTheDocument();
  });
  it("희망 시작가가 최소 희망 낙찰가보다 크면 신청할 수 없다", async () => {
    query = { isPending: false, data: product };
    const { Route } = await import("@/routes/seller/apply.$productId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.change(screen.getByPlaceholderText("1,000,000"), {
      target: { value: "20000" },
    });
    fireEvent.change(
      screen.getByPlaceholderText("구매자에게 공개되지 않습니다"),
      { target: { value: "15000" } },
    );
    fireEvent.change(
      document.querySelector(
        'input[type="datetime-local"]',
      ) as HTMLInputElement,
      { target: { value: "2099-01-01T10:00" } },
    );

    expect(
      screen.getByText(
        "최소 희망 낙찰가는 희망 시작가 이상으로 입력해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "경매 신청" })).toBeDisabled();
  });
});
