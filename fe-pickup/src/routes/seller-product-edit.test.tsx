import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queryState: { data?: unknown; isPending: boolean; isError: boolean } = {
  isPending: true,
  isError: false,
};
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
  useQuery: () => queryState,
  useMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock("@/api/consignments", () => ({ getMyConsignmentDetail: vi.fn() }));
vi.mock("@/api/generated/consignment/consignment", () => ({
  modifyConsignment: vi.fn(),
}));
vi.mock("@/api/image-upload", () => ({ uploadImage: vi.fn() }));
vi.mock("@/components/domain/consignment-image-fields", () => ({
  ConsignmentImageFields: () => <div>이미지 첨부 영역</div>,
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const product = {
  id: "8",
  cardName: "Blastoise",
  status: "REGISTERABLE",
  grade: { agency: "PSA", score: "10", serial: "A" },
  gradeCode: "GEM_MINT",
  cardState: "HIGH",
  inspectedAt: "2026-01-01",
  images: [
    { consignmentImageId: 1, imageUrl: "front" },
    { consignmentImageId: 2, imageUrl: "back" },
  ],
  setName: "Base",
  cardNumber: "2",
  language: "EN",
  rarity: "Rare",
  auctionRegistered: false,
};

describe("셀러 상품 수정", () => {
  beforeEach(() => {
    queryState = { isPending: true, isError: false };
  });

  it("로딩·오류·수정 불가 상태를 처리한다", async () => {
    const { Route } = await import("@/routes/seller/products/$productId_.edit");
    const Component = Route.options.component as ComponentType;
    const view = render(<Component />);
    expect(view.container).toBeEmptyDOMElement();
    view.unmount();
    queryState = { isPending: false, isError: true };
    render(<Component />);
    expect(
      screen.getByText("상품 정보를 불러오지 못했습니다."),
    ).toBeInTheDocument();
    queryState = {
      data: { ...product, status: "AUCTION_LIVE" },
      isPending: false,
      isError: false,
    };
    render(<Component />);
    expect(
      screen.getByText("지금은 정보를 수정할 수 없는 상품이에요."),
    ).toBeInTheDocument();
    queryState = { data: undefined, isPending: false, isError: false };
    render(<Component />);
    expect(screen.getByText("상품을 찾을 수 없습니다.")).toBeInTheDocument();
  });

  it("수정 가능한 상품의 입력 폼을 표시한다", async () => {
    queryState = { data: product, isPending: false, isError: false };
    const { Route } = await import("@/routes/seller/products/$productId_.edit");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "Blastoise 정보 수정" }),
    ).toBeInTheDocument();
    expect(screen.getByText("이미지 첨부 영역")).toBeInTheDocument();
    expect(screen.getByDisplayValue("상")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장하기" })).toBeEnabled();
  });

  it("주요 결함 입력란에 글자수 제한과 카운터를 표시한다", async () => {
    queryState = { data: product, isPending: false, isError: false };
    const { Route } = await import("@/routes/seller/products/$productId_.edit");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    const defectInput =
      screen.getByPlaceholderText("예: 뒷면 우하단 미세 스크래치");
    expect(defectInput).toHaveAttribute("maxLength", "255");
    expect(screen.getByText("0/255")).toBeInTheDocument();
    fireEvent.change(defectInput, { target: { value: "모서리 마모" } });
    expect(screen.getByText("6/255")).toBeInTheDocument();
  });

  it("감정일에 미래 날짜를 입력하면 저장할 수 없다", async () => {
    queryState = { data: product, isPending: false, isError: false };
    const { Route } = await import("@/routes/seller/products/$productId_.edit");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    const tomorrow = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000)
      .toISOString()
      .slice(0, 10);
    fireEvent.change(
      document.querySelector('input[type="date"]') as HTMLInputElement,
      { target: { value: tomorrow } },
    );
    expect(
      screen.getByText("현재 날짜보다 이후일 수 없습니다."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장하기" })).toBeDisabled();
  });
});
