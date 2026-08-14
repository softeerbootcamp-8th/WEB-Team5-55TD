import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ImageCropDialog } from "@/components/domain/image-crop-dialog";
import type { CropArea } from "@/lib/image-crop";

// react-easy-crop 은 컨테이너 크기를 측정하는데 jsdom 에서는 0이라 동작하지 않는다.
// 콜백을 직접 쏘는 버튼으로 대체한다.
vi.mock("react-easy-crop", () => ({
  default: ({
    onMediaLoaded,
    onCropComplete,
    onCropSizeChange,
    cropSize,
  }: {
    onMediaLoaded: (media: {
      width: number;
      height: number;
      naturalWidth: number;
      naturalHeight: number;
    }) => void;
    onCropComplete: (area: unknown, areaPixels: CropArea) => void;
    onCropSizeChange: (size: { width: number; height: number }) => void;
    cropSize?: { width: number; height: number };
  }) => (
    <div>
      <button
        type="button"
        onClick={() =>
          onMediaLoaded({
            width: 400,
            height: 300,
            naturalWidth: 2000,
            naturalHeight: 1500,
          })
        }
      >
        mock-loaded
      </button>
      <button
        type="button"
        onClick={() => onCropSizeChange({ width: 240, height: 180 })}
      >
        mock-size
      </button>
      <button
        type="button"
        onClick={() =>
          onCropComplete({}, { x: 10, y: 20, width: 800, height: 800 })
        }
      >
        mock-crop
      </button>
      <span data-testid="crop-size">
        {cropSize ? `${cropSize.width}×${cropSize.height}` : "auto"}
      </span>
    </div>
  ),
}));

const renderCroppedFile = vi.hoisted(() => vi.fn());
vi.mock("@/lib/image-crop", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/image-crop")>()),
  renderCroppedFile,
}));

function makeFile(name: string) {
  return new File(["x"], name, { type: "image/jpeg" });
}

function setup(names: string[], onFinish = vi.fn()) {
  renderCroppedFile.mockClear();
  renderCroppedFile.mockImplementation(async (file: File) => {
    return new File(["y"], `cropped-${file.name}`, { type: "image/jpeg" });
  });
  render(
    <ImageCropDialog
      files={names.map(makeFile)}
      purpose="CONSIGNMENT"
      onFinish={onFinish}
    />,
  );
  return onFinish;
}

function firePointer(
  element: Element,
  type: "pointerdown" | "pointermove" | "pointerup",
  clientX: number,
  clientY: number,
) {
  const event = new Event(type, { bubbles: true });
  Object.defineProperties(event, {
    pointerId: { value: 1 },
    clientX: { value: clientX },
    clientY: { value: clientY },
  });
  fireEvent(element, event);
}

const loadMedia = () => fireEvent.click(screen.getByText("mock-loaded"));
const completeCrop = () => fireEvent.click(screen.getByText("mock-crop"));

describe("이미지 크롭 다이얼로그", () => {
  it("여러 장이면 진행 상황을 보여주고 한 장씩 처리한다", async () => {
    const onFinish = setup(["a.jpg", "b.jpg"]);
    expect(screen.getByText(/2장 중 1번째/)).toBeInTheDocument();

    loadMedia();
    completeCrop();
    fireEvent.click(screen.getByRole("button", { name: "적용" }));

    await waitFor(() =>
      expect(screen.getByText(/2장 중 2번째/)).toBeInTheDocument(),
    );
    expect(onFinish).not.toHaveBeenCalled();

    loadMedia();
    completeCrop();
    fireEvent.click(screen.getByRole("button", { name: "적용" }));

    await waitFor(() => expect(onFinish).toHaveBeenCalled());
    expect(onFinish.mock.calls[0][0].map((file: File) => file.name)).toEqual([
      "cropped-a.jpg",
      "cropped-b.jpg",
    ]);
  });

  it("건너뛰기는 자르지 않고 전체 영역으로 저장한다", async () => {
    const onFinish = setup(["a.jpg"]);
    loadMedia();

    fireEvent.click(screen.getByRole("button", { name: "건너뛰기" }));

    await waitFor(() => expect(onFinish).toHaveBeenCalled());
    // 기본 비율이 1:1 이므로 2000×1500 의 가운데 정사각형을 쓴다.
    expect(renderCroppedFile.mock.calls[0][1]).toEqual({
      x: 250,
      y: 0,
      width: 1500,
      height: 1500,
    });
  });

  it("전체 취소는 아무것도 돌려주지 않는다", () => {
    const onFinish = setup(["a.jpg", "b.jpg"]);

    fireEvent.click(screen.getByRole("button", { name: "전체 취소" }));

    expect(onFinish).toHaveBeenCalledWith([]);
  });

  it("잘라낸 영역이 권장 해상도보다 작으면 경고한다", () => {
    setup(["a.jpg"]);
    loadMedia();
    completeCrop();

    // 800px 크롭 → 권장 1200px 미달
    expect(screen.getByText(/권장 해상도/)).toBeInTheDocument();
    expect(screen.getByText(/저장 크기 800 × 800/)).toBeInTheDocument();
  });

  it("한 장만 고르면 큐 관련 버튼을 보여주지 않는다", () => {
    setup(["a.jpg"]);

    expect(
      screen.queryByRole("button", { name: "모두 이대로" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/장 중/)).not.toBeInTheDocument();
  });

  it("비율 프리셋을 고를 수 있다", () => {
    setup(["a.jpg"]);

    const free = screen.getByRole("button", { name: "자유" });
    expect(screen.getByRole("button", { name: "1:1" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    fireEvent.click(free);

    expect(free).toHaveAttribute("aria-pressed", "true");
  });

  it("자유 비율은 모서리를 마우스로 드래그해 영역 크기를 바꾼다", () => {
    setup(["a.jpg"]);
    fireEvent.click(screen.getByRole("button", { name: "자유" }));
    fireEvent.click(screen.getByText("mock-size"));

    expect(screen.getByTestId("crop-size")).toHaveTextContent("240×180");
    const handle = screen.getByRole("button", {
      name: "오른쪽 아래 자르기 영역 크기 조절",
    });
    firePointer(handle, "pointerdown", 0, 0);
    firePointer(handle, "pointermove", 20, 10);
    firePointer(handle, "pointerup", 20, 10);

    expect(screen.getByTestId("crop-size")).toHaveTextContent("280×200");
  });
});
