import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Avatar } from "@/components/domain/avatar";
import { CardThumb } from "@/components/domain/card-thumb";
import { ConnectionStatus } from "@/components/domain/connection-status";
import { Countdown } from "@/components/domain/countdown";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { EmptyState, SectionHeader } from "@/components/domain/section-header";
import { ResultBadge, StatusBadge } from "@/components/domain/status-badge";
import { AuctionStatus, GradeAgency } from "@/lib/types";

afterEach(() => {
  vi.useRealTimers();
});

describe("공통 도메인 컴포넌트", () => {
  it("가격에 라벨, 금액, 강조 여부를 반영한다", () => {
    const { rerender } = render(
      <Price amount={1280000} label="현재가" size="lg" />,
    );
    expect(screen.getByText("현재가")).toBeInTheDocument();
    expect(screen.getByText("128만원")).toBeInTheDocument();
    expect(screen.getByText("128만원")).toHaveClass(
      "text-[var(--color-price)]",
    );
    expect(screen.getByText("128만원")).toHaveAttribute("title", "1,280,000원");

    rerender(<Price amount={1280000} emphasize={false} />);
    expect(screen.getByText("128만원")).toHaveClass("text-foreground");
  });

  it("경매 상태와 낙찰 결과를 표시한다", () => {
    const { rerender } = render(<StatusBadge status={AuctionStatus.LIVE} />);
    expect(screen.getByText("진행 중")).toBeInTheDocument();
    rerender(<StatusBadge status={AuctionStatus.UPCOMING} />);
    expect(screen.getByText("예정")).toBeInTheDocument();
    rerender(<StatusBadge status={AuctionStatus.ENDED} />);
    expect(screen.getByText("종료")).toBeInTheDocument();
    rerender(<ResultBadge won />);
    expect(screen.getByText("낙찰")).toBeInTheDocument();
    rerender(<ResultBadge won={false} />);
    expect(screen.getByText("유찰")).toBeInTheDocument();
  });

  it("감정 등급이 없으면 숨기고 있으면 표시한다", () => {
    const { rerender } = render(<GradeBadge />);
    expect(screen.queryByText(/PSA/)).not.toBeInTheDocument();
    rerender(<GradeBadge grade={{ agency: GradeAgency.PSA, score: "10" }} />);
    expect(screen.getByText("PSA 10")).toBeInTheDocument();
  });

  it("프로필 이미지가 없으면 이니셜을, 있으면 이미지를 표시한다", () => {
    const { rerender } = render(<Avatar nickname="alice" />);
    expect(screen.getByText("A")).toBeInTheDocument();
    rerender(<Avatar nickname="alice" src="/avatar.png" />);
    expect(
      screen.getByRole("img", { name: "alice 프로필 이미지" }),
    ).toHaveAttribute("src", "/avatar.png");
  });

  it("프로필 이미지 로드가 실패하면 지정된 대체 이미지를 보여준다", () => {
    render(
      <Avatar
        nickname="alice"
        src="/broken-profile.png"
        fallbackSrc="/pokemon.png"
      />,
    );
    const image = screen.getByRole("img", { name: "alice 프로필 이미지" });

    fireEvent.error(image);

    expect(image).toHaveAttribute("src", "/pokemon.png");
  });

  it("카드 썸네일에 등급과 라벨을 표시하고 이미지 로딩 후 텍스트를 숨긴다", () => {
    render(
      <CardThumb
        cardName="Charizard"
        grade={{ agency: GradeAgency.BGS, score: "9.5" }}
        imageUrl="/card.png"
        label="LIVE"
      />,
    );
    expect(screen.getByText("Charizard")).toBeInTheDocument();
    expect(screen.getByText("BGS 9.5")).toBeInTheDocument();
    expect(screen.getByText("LIVE")).toBeInTheDocument();
    fireEvent.load(screen.getByRole("img", { name: "Charizard" }));
    expect(screen.getByText("Charizard")).toHaveClass("sr-only");
    // 실제 사진이 뜨면 GradeBadge와 중복되는 등급/라벨 오버레이도 함께 숨긴다.
    expect(screen.getByText("BGS 9.5")).toHaveClass("sr-only");
    expect(screen.getByText("LIVE")).toHaveClass("sr-only");
  });

  it("섹션 헤더와 빈 상태의 선택적 콘텐츠를 표시한다", () => {
    render(
      <>
        <SectionHeader
          title="경매"
          description="진행 중인 경매"
          action={<button>정렬</button>}
        />
        <EmptyState
          title="목록이 없습니다"
          description="새 항목을 추가해보세요"
          action={<button>추가</button>}
        />
      </>,
    );
    expect(screen.getByRole("heading", { name: "경매" })).toBeInTheDocument();
    expect(screen.getByText("진행 중인 경매")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "정렬" })).toBeInTheDocument();
    expect(screen.getByText("목록이 없습니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "추가" })).toBeInTheDocument();
  });

  it("카운트다운이 종료되면 onEnd를 한 번 호출한다", () => {
    vi.useFakeTimers();
    const now = new Date("2026-08-08T12:00:00Z");
    vi.setSystemTime(now);
    const onEnd = vi.fn();
    render(
      <Countdown
        to={new Date(now.getTime() + 1000).toISOString()}
        onEnd={onEnd}
      />,
    );
    expect(screen.getByText("00 : 00 : 00 : 01")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1000));
    expect(onEnd).toHaveBeenCalledOnce();
    act(() => vi.advanceTimersByTime(2000));
    expect(onEnd).toHaveBeenCalledOnce();
  });

  it("연결 상태마다 라벨과 색을 다르게 보여준다", () => {
    const { rerender } = render(<ConnectionStatus status="connected" />);
    expect(screen.getByRole("status")).toHaveTextContent("실시간");
    expect(screen.getByRole("status")).toHaveClass(
      "text-[var(--color-success)]",
    );

    rerender(<ConnectionStatus status="reconnecting" />);
    expect(screen.getByRole("status")).toHaveTextContent("재연결 중");
    expect(screen.getByRole("status")).toHaveClass(
      "text-[var(--color-warning)]",
    );

    rerender(<ConnectionStatus status="disconnected" />);
    expect(screen.getByRole("status")).toHaveTextContent("연결 끊김");
    expect(screen.getByRole("status")).toHaveClass(
      "text-[var(--color-danger)]",
    );
  });
});
