import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clampCropRect,
  getFullImageCrop,
  getOutputSize,
  IMAGE_CROP_PRESETS,
  isBelowRecommended,
  renameWithExtension,
  renderCroppedFile,
  resolveOutputType,
} from "@/lib/image-crop";

const CONSIGNMENT = IMAGE_CROP_PRESETS.CONSIGNMENT;
const PROFILE = IMAGE_CROP_PRESETS.PROFILE;

describe("출력 포맷 결정", () => {
  it("상품 이미지는 원본 포맷과 무관하게 JPEG로 저장한다", () => {
    expect(resolveOutputType(CONSIGNMENT, "image/png")).toBe("image/jpeg");
    expect(resolveOutputType(CONSIGNMENT, "image/webp")).toBe("image/jpeg");
  });

  it("프로필 이미지는 원본 포맷을 유지한다", () => {
    expect(resolveOutputType(PROFILE, "image/png")).toBe("image/png");
    expect(resolveOutputType(PROFILE, "image/webp")).toBe("image/webp");
    expect(resolveOutputType(PROFILE, "image/jpeg")).toBe("image/jpeg");
  });

  it("허용 목록에 없는 원본 포맷이면 JPEG로 떨어뜨린다", () => {
    expect(resolveOutputType(PROFILE, "image/gif")).toBe("image/jpeg");
  });
});

describe("표시용 파일명", () => {
  it("확장자를 실제 저장 포맷에 맞춘다", () => {
    expect(renameWithExtension("card.png", "image/jpeg")).toBe("card.jpg");
    expect(renameWithExtension("card.jpeg", "image/png")).toBe("card.png");
    expect(renameWithExtension("my.card.v2.webp", "image/jpeg")).toBe(
      "my.card.v2.jpg",
    );
  });

  it("확장자가 없거나 이름이 비어도 안전하게 만든다", () => {
    expect(renameWithExtension("card", "image/webp")).toBe("card.webp");
    expect(renameWithExtension(".hidden", "image/jpeg")).toBe(".hidden.jpg");
  });
});

describe("출력 크기", () => {
  it("긴 변을 권장 해상도로 줄인다", () => {
    expect(getOutputSize({ width: 2000, height: 1500 }, 1200)).toEqual({
      width: 1200,
      height: 900,
    });
  });

  it("원본이 권장보다 작으면 확대하지 않는다", () => {
    expect(getOutputSize({ width: 300, height: 300 }, 1200)).toEqual({
      width: 300,
      height: 300,
    });
  });

  it("권장 해상도 미달을 판정한다", () => {
    expect(isBelowRecommended({ width: 800, height: 600 }, 1200)).toBe(true);
    expect(isBelowRecommended({ width: 1200, height: 900 }, 1200)).toBe(false);
  });
});

describe("크롭 영역 보정", () => {
  it("음수 좌표와 경계 초과를 원본 안으로 맞춘다", () => {
    expect(
      clampCropRect({ x: -0.4, y: -2, width: 500, height: 500 }, 400, 300),
    ).toEqual({ x: 0, y: 0, width: 400, height: 300 });
  });

  it("소수 좌표를 정수로 반올림한다", () => {
    expect(
      clampCropRect(
        { x: 10.6, y: 20.2, width: 100.4, height: 100.5 },
        400,
        300,
      ),
    ).toEqual({ x: 11, y: 20, width: 100, height: 101 });
  });
});

describe("전체 영역 크롭", () => {
  it("비율이 없으면 원본 전체를 쓴다", () => {
    expect(getFullImageCrop(400, 300)).toEqual({
      x: 0,
      y: 0,
      width: 400,
      height: 300,
    });
  });

  it("비율이 있으면 가운데 기준 최대 사각형을 쓴다", () => {
    expect(getFullImageCrop(400, 300, 1)).toEqual({
      x: 50,
      y: 0,
      width: 300,
      height: 300,
    });
  });
});

describe("크롭 렌더링", () => {
  const calls: string[] = [];
  let fillStyle = "";
  let drawArgs: unknown[] = [];
  let blobQueue: (Blob | null)[] = [];
  let originalGetContext: typeof HTMLCanvasElement.prototype.getContext;
  let originalToBlob: typeof HTMLCanvasElement.prototype.toBlob;

  function makeBlob(type: string, size: number): Blob {
    const blob = new Blob(["x"], { type });
    Object.defineProperty(blob, "size", { value: size });
    return blob;
  }

  beforeEach(() => {
    calls.length = 0;
    drawArgs = [];
    fillStyle = "";
    blobQueue = [];

    originalGetContext = HTMLCanvasElement.prototype.getContext;
    originalToBlob = HTMLCanvasElement.prototype.toBlob;

    const context = {
      imageSmoothingEnabled: false,
      imageSmoothingQuality: "low",
      set fillStyle(value: string) {
        fillStyle = value;
      },
      get fillStyle() {
        return fillStyle;
      },
      fillRect: () => calls.push("fillRect"),
      drawImage: (...args: unknown[]) => {
        calls.push("drawImage");
        drawArgs = args;
      },
    };
    HTMLCanvasElement.prototype.getContext = vi.fn(
      () => context,
    ) as unknown as typeof HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.toBlob = vi.fn((callback: BlobCallback) => {
      // 큐가 비었을 때만 기본값을 준다 — 명시적으로 넣은 null(인코딩 실패)을 삼키지 않도록.
      callback(
        blobQueue.length > 0
          ? (blobQueue.shift() as Blob | null)
          : makeBlob("image/jpeg", 1000),
      );
    }) as unknown as typeof HTMLCanvasElement.prototype.toBlob;

    globalThis.createImageBitmap = vi.fn(async () => ({
      width: 2000,
      height: 1500,
      close: () => {},
    })) as unknown as typeof createImageBitmap;
  });

  afterEach(() => {
    HTMLCanvasElement.prototype.getContext = originalGetContext;
    HTMLCanvasElement.prototype.toBlob = originalToBlob;
  });

  const sourceFile = () => new File(["x"], "card.png", { type: "image/png" });
  const crop = { x: 100, y: 50, width: 800, height: 800 };

  it("요청과 다른 포맷이 나와도 실제 결과 타입을 따른다", async () => {
    // canvas.toBlob 은 인코더 미지원 시 조용히 PNG 로 폴백한다.
    blobQueue = [makeBlob("image/png", 2000)];

    const result = await renderCroppedFile(sourceFile(), crop, PROFILE);

    expect(result.type).toBe("image/png");
    expect(result.name).toBe("card.png");
  });

  it("상품 이미지는 흰 배경을 먼저 깔고 그린다", async () => {
    blobQueue = [makeBlob("image/jpeg", 2000)];

    await renderCroppedFile(sourceFile(), crop, CONSIGNMENT);

    expect(calls).toEqual(["fillRect", "drawImage"]);
    expect(fillStyle).toBe("#ffffff");
  });

  it("프로필 PNG는 배경을 깔지 않아 투명도가 남는다", async () => {
    blobQueue = [makeBlob("image/png", 2000)];

    await renderCroppedFile(sourceFile(), crop, PROFILE);

    expect(calls).toEqual(["drawImage"]);
  });

  it("크롭 영역과 축소된 출력 크기로 그린다", async () => {
    blobQueue = [makeBlob("image/jpeg", 2000)];

    await renderCroppedFile(sourceFile(), crop, CONSIGNMENT);

    expect(drawArgs.slice(1)).toEqual([100, 50, 800, 800, 0, 0, 800, 800]);
  });

  it("10MB를 넘으면 JPEG로 다시 인코딩한다", async () => {
    blobQueue = [
      makeBlob("image/png", 11 * 1024 * 1024),
      makeBlob("image/jpeg", 900_000),
    ];

    const result = await renderCroppedFile(sourceFile(), crop, PROFILE);

    expect(result.type).toBe("image/jpeg");
    expect(result.name).toBe("card.jpg");
    expect(calls).toEqual(["drawImage", "fillRect", "drawImage"]);
  });

  it("인코딩에 실패하면 안내 메시지로 실패한다", async () => {
    blobQueue = [null];

    await expect(
      renderCroppedFile(sourceFile(), crop, CONSIGNMENT),
    ).rejects.toThrow("이미지를 처리하지 못했습니다");
  });

  it("허용하지 않는 포맷이 나오면 실패한다", async () => {
    blobQueue = [makeBlob("image/gif", 2000)];

    await expect(
      renderCroppedFile(sourceFile(), crop, PROFILE),
    ).rejects.toThrow("이미지를 처리하지 못했습니다");
  });
});
