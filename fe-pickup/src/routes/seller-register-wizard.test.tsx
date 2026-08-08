import { fireEvent, render, screen, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let cardData: { items: Array<Record<string, unknown>> } | undefined;
const mutate = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  useNavigate: () => vi.fn(),
}));
vi.mock("@tanstack/react-query", () => ({
  useMutation: () => ({ mutate, isPending: false }),
}));
vi.mock("@/api/generated/card/card", () => ({
  useSearchCards: () => ({ data: cardData, isFetching: false }),
}));
vi.mock("@/api/generated/consignment/consignment", () => ({
  registerConsignment: vi.fn(),
}));
vi.mock("@/api/image-upload", () => ({ uploadImage: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/components/domain/consignment-image-fields", () => ({
  ConsignmentImageFields: ({
    onChange,
  }: {
    onChange: (items: unknown[]) => void;
  }) => (
    <button
      type="button"
      onClick={() =>
        onChange([
          { kind: "new", file: new File([], "a") },
          { kind: "new", file: new File([], "b") },
        ])
      }
    >
      이미지 준비
    </button>
  ),
}));

describe("셀러 등록 위저드", () => {
  beforeEach(() => {
    cardData = {
      items: [
        {
          cardId: 1,
          cardName: "Charizard",
          setName: "Base",
          cardNumber: "4",
          language: "EN",
          rarity: "Rare",
          imageUrl: "card.jpg",
        },
      ],
    };
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("카드 선택부터 최종 확인까지 단계별 입력을 진행한다", async () => {
    const { Route } = await import("@/routes/seller/register");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    const search = screen.getByPlaceholderText("카드명 검색 (예: 리자몽)");
    fireEvent.change(search, { target: { value: "char" } });
    await new Promise((resolve) => setTimeout(resolve, 350));
    fireEvent.click(screen.getByRole("button", { name: /Charizard/ }));
    fireEvent.click(screen.getByRole("button", { name: "다음 단계" }));
    expect(screen.getByText(/인증기관\(PSA/)).toBeInTheDocument();
    fireEvent.change(screen.getByDisplayValue("등급 선택"), {
      target: { value: "GEM_MINT" },
    });
    fireEvent.change(screen.getByPlaceholderText("PSA-84213907"), {
      target: { value: "PSA-1" },
    });
    fireEvent.change(
      document.querySelector('input[type="date"]') as HTMLInputElement,
      {
        target: { value: "2026-01-01" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "다음 단계" }));
    fireEvent.click(screen.getByRole("button", { name: "이미지 준비" }));
    fireEvent.click(screen.getByRole("button", { name: "다음 단계" }));
    expect(screen.getByText("입력 정보 최종 확인")).toBeInTheDocument();
    expect(screen.getByText("Charizard")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "등록 완료" }));
    expect(mutate).toHaveBeenCalledWith(1);
  });

  it("검색 결과가 없으면 안내를 표시한다", async () => {
    cardData = { items: [] };
    const { Route } = await import("@/routes/seller/register");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    fireEvent.change(screen.getByPlaceholderText("카드명 검색 (예: 리자몽)"), {
      target: { value: "none" },
    });
    await new Promise((resolve) => setTimeout(resolve, 350));
    expect(screen.getByText("검색 결과가 없습니다.")).toBeInTheDocument();
  });
});
