import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queryState: { data?: unknown; isPending: boolean; isError: boolean } = {
  isPending: true,
  isError: false,
};
const navigate = vi.fn();
const mutate = vi.fn();

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useParams: () => ({ productId: "8" }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => navigate,
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => queryState,
  useMutation: () => ({ mutate, isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock("@/api/consignments", () => ({
  getMyConsignmentDetail: vi.fn(),
  deleteMyConsignment: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const product = {
  id: "8",
  cardName: "Blastoise",
  status: "REGISTERABLE",
  thumbnailUrl: "thumb.jpg",
  grade: { agency: "PSA", score: "10", serial: "A" },
  setName: "Base",
  cardNumber: "2",
  language: "EN",
  rarity: "Rare",
  majorDefect: undefined,
  images: [{ consignmentImageId: 1, imageUrl: "front.jpg" }],
  auctionRegistered: false,
};

describe("셀러 상품 상세", () => {
  beforeEach(() => {
    queryState = { isPending: true, isError: false };
    vi.clearAllMocks();
  });

  it("로딩·오류·상품 없음 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/seller/products/$productId");
    const Component = Route.options.component as ComponentType;
    const view = render(<Component />);
    expect(view.container).toBeEmptyDOMElement();
    view.unmount();
    queryState = { isPending: false, isError: true };
    render(<Component />);
    expect(
      screen.getByText("상품 정보를 불러오지 못했습니다."),
    ).toBeInTheDocument();
  });

  it("등록 가능 상품의 상세 정보와 삭제 다이얼로그를 표시한다", async () => {
    queryState = { data: product, isPending: false, isError: false };
    const { Route } = await import("@/routes/seller/products/$productId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "Blastoise" }),
    ).toBeInTheDocument();
    expect(screen.getByText("경매 신청")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "삭제" }));
    expect(screen.getByText("상품을 삭제할까요?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByText("상품을 삭제할까요?")).not.toBeInTheDocument();
    cleanup();
    queryState = {
      data: { ...product, status: "AUCTION_UPCOMING" },
      isPending: false,
      isError: false,
    };
    render(<Component />);
    expect(
      screen.getByText(
        "경매 예정 상태의 상품은 정보 수정·경매 취소가 불가합니다.",
      ),
    ).toBeInTheDocument();
    cleanup();
    queryState = {
      data: { ...product, status: "SOLD" },
      isPending: false,
      isError: false,
    };
    render(<Component />);
    expect(
      screen.getByText(
        "경매 시작 이후 상태의 상품은 정보를 수정할 수 없습니다.",
      ),
    ).toBeInTheDocument();
  });
});
