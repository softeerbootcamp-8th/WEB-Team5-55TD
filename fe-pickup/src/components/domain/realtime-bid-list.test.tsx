import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RealtimeBidList } from "@/components/domain/bid-list";
import type { Bid } from "@/lib/types";

const now = new Date().toISOString();

function bid(overrides: Partial<Bid> & { id: string }): Bid {
  return { maskedNickname: "ab***12", amount: 1000, createdAt: now, ...overrides };
}

describe("RealtimeBidList", () => {
  it("입찰이 없으면 빈 상태를 보여준다", () => {
    render(
      <RealtimeBidList
        bids={[]}
        hasNext={false}
        isFetchingNextPage={false}
        onLoadMore={vi.fn()}
      />,
    );
    expect(screen.getByText("아직 입찰이 없습니다.")).toBeInTheDocument();
  });

  it("입찰자별로 중복 제거해 최신 입찰만 보여준다", () => {
    render(
      <RealtimeBidList
        bids={[
          bid({ id: "3", maskedNickname: "bb***22", amount: 3000 }),
          bid({ id: "2", maskedNickname: "aa***11", amount: 2000 }),
          bid({ id: "1", maskedNickname: "bb***22", amount: 1000 }),
        ]}
        hasNext={false}
        isFetchingNextPage={false}
        onLoadMore={vi.fn()}
      />,
    );

    const rows = screen.getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("bb***22")).toBeInTheDocument();
    expect(within(rows[0]).getByText("3,000원")).toBeInTheDocument();
    expect(within(rows[1]).getByText("aa***11")).toBeInTheDocument();
  });

  it("hasNext가 true면 더 보기 버튼이 onLoadMore를 호출한다", () => {
    const onLoadMore = vi.fn();
    render(
      <RealtimeBidList
        bids={[bid({ id: "1" })]}
        hasNext
        isFetchingNextPage={false}
        onLoadMore={onLoadMore}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "더 보기" }));
    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });

  it("hasNext가 false면 더 보기 버튼을 보여주지 않는다", () => {
    render(
      <RealtimeBidList
        bids={[bid({ id: "1" })]}
        hasNext={false}
        isFetchingNextPage={false}
        onLoadMore={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "더 보기" }),
    ).not.toBeInTheDocument();
  });

  it("다음 페이지를 불러오는 중이면 버튼 문구와 비활성 상태로 알린다", () => {
    render(
      <RealtimeBidList
        bids={[bid({ id: "1" })]}
        hasNext
        isFetchingNextPage
        onLoadMore={vi.fn()}
      />,
    );
    const button = screen.getByRole("button", { name: "불러오는 중…" });
    expect(button).toBeDisabled();
  });
});
