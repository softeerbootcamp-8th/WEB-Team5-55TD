import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BidList, BidRow } from "@/components/domain/bid-list";
import { ConsignmentImageFields } from "@/components/domain/consignment-image-fields";
import { ImageLightbox } from "@/components/domain/image-lightbox";
import { StepIndicator } from "@/components/domain/step-indicator";

describe("추가 도메인 컴포넌트", () => {
  it("입찰 목록의 빈 상태와 본인 입찰을 표시한다", () => {
    const now = new Date().toISOString();
    const { rerender } = render(<BidList bids={[]} />);
    expect(screen.getByText("아직 입찰이 없습니다.")).toBeInTheDocument();
    rerender(
      <ul>
        <BidRow
          bid={{
            id: "1",
            nickname: "alpha12",
            amount: 10500,
            createdAt: now,
          }}
        />
        <BidRow
          bid={{
            id: "2",
            nickname: "me",
            amount: 11000,
            createdAt: now,
            isMine: true,
          }}
        />
      </ul>,
    );
    expect(screen.getByText("alpha12")).toBeInTheDocument();
    // 본인 입찰 행: 아바타 이니셜과 닉네임 라벨이 둘 다 "나"라 중복되므로, 라벨
    // 쪽(truncate 클래스)으로 셀렉터를 좁혀 확인한다.
    const myRow = screen.getAllByRole("listitem")[1];
    expect(
      within(myRow).getByText("나", { selector: "span.truncate" }),
    ).toBeInTheDocument();
    expect(screen.getByText("10,500원")).toBeInTheDocument();
  });

  it("전체 입찰 목록에서 프로필 사진이 없으면 포켓몬 아바타로 채운다", () => {
    const now = new Date().toISOString();
    render(
      <BidList
        bids={[
          { id: "1", nickname: "bravo22", amount: 12000, createdAt: now },
          { id: "2", nickname: "alpha11", amount: 11000, createdAt: now },
          // 같은 입찰자가 다시 등장해도 같은 아바타를 쓴다.
          { id: "3", nickname: "bravo22", amount: 10500, createdAt: now },
          {
            id: "4",
            nickname: "charlie33",
            amount: 10000,
            createdAt: now,
            profileImageUrl: "/uploaded.png",
          },
        ]}
      />,
    );

    const avatars = screen.getAllByRole("img");
    expect(avatars[0]).toHaveAttribute("src", "/avatars/pokemon/squirtle.webp");
    expect(avatars[1]).toHaveAttribute("src", "/avatars/pokemon/pikachu.webp");
    expect(avatars[2]).toHaveAttribute("src", "/avatars/pokemon/squirtle.webp");
    // 프로필 사진이 있으면 그대로 우선한다.
    expect(avatars[3]).toHaveAttribute("src", "/uploaded.png");
  });

  it("스텝 인디케이터에서 완료·현재·예정 단계를 구분한다", () => {
    render(<StepIndicator steps={["카드", "실물", "이미지"]} current={1} />);
    expect(screen.getByText("카드")).toBeInTheDocument();
    expect(screen.getByText("실물")).toHaveClass("font-semibold");
    expect(screen.getByText("이미지")).toBeInTheDocument();
  });

  it("라이트박스에서 이전·다음·점 인디케이터로 이미지를 이동한다", () => {
    const onIndexChange = vi.fn();
    render(
      <ImageLightbox
        images={["/one.png", "/two.png"]}
        index={0}
        onIndexChange={onIndexChange}
        onOpenChange={vi.fn()}
        alt="카드 이미지"
      />,
    );
    expect(screen.getByRole("img", { name: "카드 이미지" })).toHaveAttribute(
      "src",
      "/one.png",
    );
    fireEvent.click(screen.getByRole("button", { name: "다음 이미지" }));
    expect(onIndexChange).toHaveBeenCalledWith(1);
    fireEvent.click(screen.getByRole("button", { name: "이전 이미지" }));
    expect(onIndexChange).toHaveBeenCalledWith(1);
    fireEvent.click(screen.getByRole("button", { name: "2번째 이미지 보기" }));
    expect(onIndexChange).toHaveBeenCalledWith(1);
  });

  it("이미지 필드에서 유효한 새 이미지를 추가하고 삭제한다", () => {
    const onChange = vi.fn();
    const onError = vi.fn();
    const { container } = render(
      <ConsignmentImageFields
        images={[]}
        onChange={onChange}
        onError={onError}
      />,
    );
    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    const file = new File([new ArrayBuffer(10)], "card.jpg", {
      type: "image/jpeg",
    });
    fireEvent.change(input, { target: { files: [file] } });
    expect(onChange).toHaveBeenCalledWith([{ kind: "new", file }]);

    const images = [{ kind: "new" as const, file }];
    const rerendered = render(
      <ConsignmentImageFields
        images={images}
        onChange={onChange}
        onError={onError}
      />,
    );
    fireEvent.click(rerendered.getByRole("button", { name: /삭제/ }));
    expect(onChange).toHaveBeenCalledWith([]);
  });
});
