import {
  IMAGE_ACCEPT,
  MAX_IMAGE_SIZE,
  type ImagePurpose,
} from "@/api/image-upload";

/** 잘라낼 영역 (원본 픽셀 좌표) */
export interface CropArea {
  x: number;
  y: number;
  width: number;
  height: number;
}

export type AspectPreset = "square" | "card" | "free";

/** 자유 비율은 값이 없다 — react-easy-crop 에 undefined 를 넘기면 비율을 고정하지 않는다. */
export const ASPECT_VALUES: Record<AspectPreset, number | undefined> = {
  square: 1,
  card: 3 / 4,
  free: undefined,
};

export const ASPECT_LABELS: Record<AspectPreset, string> = {
  square: "1:1",
  card: "3:4",
  free: "자유",
};

export interface ImageCropPreset {
  /** 고를 수 있는 비율. 첫 번째가 기본값 */
  aspects: AspectPreset[];
  /** 오버레이 모양만 바꾼다. 출력 비트맵은 언제나 사각형이다 */
  cropShape: "rect" | "round";
  /** 긴 변 목표 픽셀. 이보다 작은 원본은 확대하지 않는다 */
  targetSize: number;
  output: "jpeg" | "preserve";
  quality: number;
  title: string;
  /** 업로드 필드 하단과 다이얼로그가 함께 쓰는 권장 사양 문구 */
  guide: string;
  /** 다이얼로그에만 노출하는 보조 힌트 */
  hint?: string;
}

const RENDER_FAILED_MESSAGE =
  "이미지를 처리하지 못했습니다. 다시 시도해 주세요.";

/** 이미지 권장 사양 (DESIGN.md §5.13) */
export const IMAGE_CROP_PRESETS: Record<ImagePurpose, ImageCropPreset> = {
  CONSIGNMENT: {
    aspects: ["square", "card", "free"],
    cropShape: "rect",
    targetSize: 1200,
    output: "jpeg",
    quality: 0.85,
    title: "상품 이미지 자르기",
    guide: "잘라낸 영역을 정사각(1:1) 1200×1200 JPG로 저장합니다.",
    hint: "PSA·BGS 슬랩은 등급 라벨이 잘리지 않게 '자유' 비율을 쓰세요.",
  },
  PROFILE: {
    aspects: ["square"],
    cropShape: "round",
    targetSize: 512,
    output: "preserve",
    quality: 0.9,
    title: "프로필 이미지 자르기",
    guide: "정사각(1:1)으로 잘라 512×512로 저장되며 원형으로 표시됩니다.",
  },
};

const ALLOWED_TYPES = new Set(IMAGE_ACCEPT.split(","));

const EXTENSION_BY_TYPE: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

export function extensionForType(mime: string): string {
  return EXTENSION_BY_TYPE[mime] ?? "jpg";
}

/** 캔버스에 요청할 출력 타입. 실제 결과는 blob.type 으로 다시 확인한다. */
export function resolveOutputType(
  preset: ImageCropPreset,
  sourceType: string,
): string {
  if (preset.output === "jpeg") return "image/jpeg";
  return ALLOWED_TYPES.has(sourceType) ? sourceType : "image/jpeg";
}

/** 표시용 파일명의 확장자를 실제 저장 포맷에 맞춘다(서버는 자체 키를 만들므로 표시 목적). */
export function renameWithExtension(name: string, mime: string): string {
  const extension = extensionForType(mime);
  const dot = name.lastIndexOf(".");
  const base = dot > 0 ? name.slice(0, dot) : name;
  return `${base || "image"}.${extension}`;
}

/** 원본보다 크게 만들지 않는다 — 확대는 용량만 늘고 화질은 그대로다. */
export function getOutputSize(
  crop: Pick<CropArea, "width" | "height">,
  targetSize: number,
): { width: number; height: number } {
  const longest = Math.max(crop.width, crop.height);
  const scale = longest > 0 ? Math.min(1, targetSize / longest) : 1;
  return {
    width: Math.max(1, Math.round(crop.width * scale)),
    height: Math.max(1, Math.round(crop.height * scale)),
  };
}

/**
 * 크롭 영역을 원본 경계 안의 정수 좌표로 맞춘다.
 * react-easy-crop 의 croppedAreaPixels 는 반올림 때문에 음수나 경계 초과가 나올 수 있고,
 * 그대로 drawImage 하면 가장자리에 빈 1px 이 생겨 JPEG 에서 검은 선으로 보인다.
 */
export function clampCropRect(
  crop: CropArea,
  sourceWidth: number,
  sourceHeight: number,
): CropArea {
  const x = Math.min(
    Math.max(0, Math.round(crop.x)),
    Math.max(0, sourceWidth - 1),
  );
  const y = Math.min(
    Math.max(0, Math.round(crop.y)),
    Math.max(0, sourceHeight - 1),
  );
  return {
    x,
    y,
    width: Math.max(1, Math.min(Math.round(crop.width), sourceWidth - x)),
    height: Math.max(1, Math.min(Math.round(crop.height), sourceHeight - y)),
  };
}

/** 자르지 않고 전체를 쓰는 영역. 비율이 주어지면 가운데 기준 최대 사각형. */
export function getFullImageCrop(
  width: number,
  height: number,
  aspect?: number,
): CropArea {
  if (!aspect) return { x: 0, y: 0, width, height };

  const cropWidth = Math.min(width, height * aspect);
  const cropHeight = cropWidth / aspect;
  return {
    x: (width - cropWidth) / 2,
    y: (height - cropHeight) / 2,
    width: cropWidth,
    height: cropHeight,
  };
}

/** 권장 해상도에 못 미치는지 — 차단하지 않고 안내만 한다. */
export function isBelowRecommended(
  crop: Pick<CropArea, "width" | "height">,
  targetSize: number,
): boolean {
  return Math.max(crop.width, crop.height) < targetSize;
}

interface DecodedImage {
  source: CanvasImageSource;
  width: number;
  height: number;
  close: () => void;
}

/**
 * EXIF 회전을 적용해 디코딩한다. 크롭 미리보기(<img>)와 캔버스 출력의 좌표계가
 * 어긋나 저장 결과만 90° 돌아가는 것을 막는다.
 */
export async function decodeImage(file: File): Promise<DecodedImage> {
  if (typeof createImageBitmap === "function") {
    const bitmap = await createImageBitmap(file, {
      imageOrientation: "from-image",
    });
    return {
      source: bitmap,
      width: bitmap.width,
      height: bitmap.height,
      close: () => bitmap.close(),
    };
  }

  const objectUrl = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = objectUrl;
    await image.decode();
    return {
      source: image,
      width: image.naturalWidth,
      height: image.naturalHeight,
      close: () => URL.revokeObjectURL(objectUrl),
    };
  } catch (error) {
    URL.revokeObjectURL(objectUrl);
    throw error;
  }
}

function toBlob(
  canvas: HTMLCanvasElement,
  type: string,
  quality: number,
): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

/**
 * 잘라낸 영역을 권장 해상도로 줄여 새 File 로 만든다.
 *
 * canvas.toBlob 은 인코더를 지원하지 않으면 조용히 PNG 로 폴백하므로, 선언하는
 * MIME 은 요청값이 아니라 결과 blob.type 이어야 한다 — 서버 finalize 가 파일
 * 시그니처를 검사하기 때문에 어긋나면 업로드가 끝난 뒤에야 거절된다.
 */
export async function renderCroppedFile(
  file: File,
  crop: CropArea,
  preset: ImageCropPreset,
): Promise<File> {
  const decoded = await decodeImage(file);

  try {
    const rect = clampCropRect(crop, decoded.width, decoded.height);
    const size = getOutputSize(rect, preset.targetSize);

    const canvas = document.createElement("canvas");
    canvas.width = size.width;
    canvas.height = size.height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error(RENDER_FAILED_MESSAGE);

    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";

    const draw = (opaque: boolean) => {
      if (opaque) {
        // JPEG 에는 알파가 없어 투명 영역이 검정으로 합성된다. 흰 배경을 먼저 깐다.
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, size.width, size.height);
      }
      context.drawImage(
        decoded.source,
        rect.x,
        rect.y,
        rect.width,
        rect.height,
        0,
        0,
        size.width,
        size.height,
      );
    };

    const requestedType = resolveOutputType(preset, file.type);
    draw(requestedType === "image/jpeg");
    let blob = await toBlob(canvas, requestedType, preset.quality);

    if (blob && blob.size > MAX_IMAGE_SIZE && blob.type !== "image/jpeg") {
      draw(true);
      blob = await toBlob(canvas, "image/jpeg", preset.quality);
    }

    if (!blob) throw new Error(RENDER_FAILED_MESSAGE);
    if (!ALLOWED_TYPES.has(blob.type)) throw new Error(RENDER_FAILED_MESSAGE);
    if (blob.size > MAX_IMAGE_SIZE) {
      throw new Error("이미지는 10MB 이하의 파일이어야 합니다.");
    }

    return new File([blob], renameWithExtension(file.name, blob.type), {
      type: blob.type,
      lastModified: Date.now(),
    });
  } finally {
    decoded.close();
  }
}
