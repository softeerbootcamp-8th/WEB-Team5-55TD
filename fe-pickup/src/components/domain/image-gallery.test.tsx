import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ImageGallery } from "@/components/domain/image-gallery";

vi.mock("@/components/domain/image-lightbox", () => ({
  ImageLightbox: ({ index, alt }: { index: number; alt: string }) => (
    <div role="dialog">{`${alt} ${index + 1}번째 확대`}</div>
  ),
}));

describe("이미지 갤러리", () => {
  it("이미지가 한 장이면 썸네일 줄을 그리지 않는다", () => {
    render(<ImageGallery images={["only.jpg"]} cardName="Mewtwo" />);

    expect(
      screen.queryByRole("button", { name: /Mewtwo 이미지 \d+/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Mewtwo 이미지 확대" }),
    ).toBeInTheDocument();
  });

  it("확대는 현재 대표로 선택된 사진에서 시작한다", () => {
    render(
      <ImageGallery images={["a.jpg", "b.jpg", "c.jpg"]} cardName="Mewtwo" />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Mewtwo 이미지 3" }));
    fireEvent.click(screen.getByRole("button", { name: "Mewtwo 이미지 확대" }));

    expect(screen.getByRole("dialog")).toHaveTextContent("3번째 확대");
  });

  it("이미지가 없으면 확대 버튼을 비활성화한다", () => {
    render(<ImageGallery images={[]} cardName="Mewtwo" />);

    expect(screen.getByRole("button")).toBeDisabled();
  });
});
