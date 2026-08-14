import { useCallback, useMemo, useRef, useState } from "react";
import Cropper from "react-easy-crop";
import type { ImagePurpose } from "@/api/image-upload";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  ASPECT_LABELS,
  ASPECT_VALUES,
  getFullImageCrop,
  getOutputSize,
  IMAGE_CROP_PRESETS,
  isBelowRecommended,
  renderCroppedFile,
  type AspectPreset,
  type CropArea,
} from "@/lib/image-crop";

const MIN_ZOOM = 1;
const MAX_ZOOM = 3;

/**
 * 이미지 크기 조절 다이얼로그 (DESIGN.md §5.12).
 * 여러 장을 한 번에 고른 경우 큐로 한 장씩 처리한다.
 */
export function ImageCropDialog({
  files,
  purpose,
  onFinish,
}: {
  files: File[];
  purpose: ImagePurpose;
  onFinish: (files: File[]) => void;
}) {
  const preset = IMAGE_CROP_PRESETS[purpose];
  const [index, setIndex] = useState(0);
  const [aspectPreset, setAspectPreset] = useState<AspectPreset>(
    preset.aspects[0],
  );
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedArea, setCroppedArea] = useState<CropArea | null>(null);
  const [naturalSize, setNaturalSize] = useState<{
    width: number;
    height: number;
  } | null>(null);
  const [isRendering, setIsRendering] = useState(false);
  const doneRef = useRef<File[]>([]);

  const file = files[index];
  const objectUrl = useMemo(
    () => (file ? URL.createObjectURL(file) : ""),
    [file],
  );

  // URL 해제는 이펙트 정리가 아니라 우리가 제어하는 전환 시점(다음 장·종료)에서 한다.
  // 이펙트 정리에 두면 StrictMode 의 이중 호출이 살아 있는 URL 을 revoke 해버려
  // 이미지가 영영 로드되지 않는다(적용·건너뛰기 버튼이 계속 비활성으로 남는다).
  const release = useCallback(() => {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }, [objectUrl]);

  const finish = useCallback(
    (rendered: File[]) => {
      release();
      onFinish(rendered);
    },
    [onFinish, release],
  );

  const goNext = useCallback(
    (rendered: File) => {
      doneRef.current = [...doneRef.current, rendered];
      release();
      if (index + 1 >= files.length) {
        onFinish(doneRef.current);
        return;
      }
      setIndex(index + 1);
      setCrop({ x: 0, y: 0 });
      setZoom(1);
      setCroppedArea(null);
      setNaturalSize(null);
    },
    [files.length, index, onFinish, release],
  );

  const commit = useCallback(
    async (area: CropArea) => {
      if (!file) return;
      setIsRendering(true);
      try {
        goNext(await renderCroppedFile(file, area, preset));
      } catch {
        // 렌더에 실패하면 원본을 그대로 쓴다 — 등록 자체가 막히지 않게.
        goNext(file);
      } finally {
        setIsRendering(false);
      }
    },
    [file, goNext, preset],
  );

  /** 자르지 않고 전체(비율이 있으면 가운데 최대 사각형)를 쓴다. 축소는 그대로 적용된다. */
  const fullArea = useCallback(() => {
    if (!naturalSize) return null;
    return getFullImageCrop(
      naturalSize.width,
      naturalSize.height,
      ASPECT_VALUES[aspectPreset],
    );
  }, [aspectPreset, naturalSize]);

  const skipRest = useCallback(async () => {
    setIsRendering(true);
    const rendered = [...doneRef.current];
    for (const remaining of files.slice(index)) {
      try {
        const size = await imageSize(remaining);
        rendered.push(
          await renderCroppedFile(
            remaining,
            getFullImageCrop(
              size.width,
              size.height,
              ASPECT_VALUES[aspectPreset],
            ),
            preset,
          ),
        );
      } catch {
        rendered.push(remaining);
      }
    }
    setIsRendering(false);
    finish(rendered);
  }, [aspectPreset, files, finish, index, preset]);

  const outputSize = croppedArea
    ? getOutputSize(croppedArea, preset.targetSize)
    : null;
  const tooSmall = croppedArea
    ? isBelowRecommended(croppedArea, preset.targetSize)
    : false;
  const hasQueue = files.length > 1;

  return (
    <Dialog open onOpenChange={(open) => !open && finish([])}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{preset.title}</DialogTitle>
          <DialogDescription>
            {hasQueue
              ? `${files.length}장 중 ${index + 1}번째 · ${file?.name ?? ""}`
              : (file?.name ?? "")}
          </DialogDescription>
        </DialogHeader>

        <div className="relative h-64 w-full overflow-hidden rounded-[var(--radius-md)] bg-black/80">
          {objectUrl && (
            <Cropper
              image={objectUrl}
              crop={crop}
              zoom={zoom}
              aspect={ASPECT_VALUES[aspectPreset]}
              cropShape={preset.cropShape === "round" ? "round" : "rect"}
              showGrid={preset.cropShape !== "round"}
              onCropChange={setCrop}
              onZoomChange={setZoom}
              onMediaLoaded={(media) =>
                setNaturalSize({
                  width: media.naturalWidth,
                  height: media.naturalHeight,
                })
              }
              onCropComplete={(_area, areaPixels) => setCroppedArea(areaPixels)}
            />
          )}
        </div>

        {preset.aspects.length > 1 && (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-[var(--color-text-sub)]">비율</span>
            {preset.aspects.map((option) => (
              <Button
                key={option}
                type="button"
                size="sm"
                variant={option === aspectPreset ? "default" : "secondary"}
                onClick={() => setAspectPreset(option)}
                aria-pressed={option === aspectPreset}
              >
                {ASPECT_LABELS[option]}
              </Button>
            ))}
          </div>
        )}

        <div className="flex items-center gap-3 text-sm">
          <label htmlFor="crop-zoom" className="text-[var(--color-text-sub)]">
            확대
          </label>
          <input
            id="crop-zoom"
            type="range"
            min={MIN_ZOOM}
            max={MAX_ZOOM}
            step={0.01}
            value={zoom}
            onChange={(event) => setZoom(Number(event.target.value))}
            className="flex-1 accent-[var(--color-primary)]"
          />
        </div>

        <div className="flex flex-col gap-1 text-xs">
          <p className="text-[var(--color-text-muted)]">
            {outputSize
              ? `저장 크기 ${outputSize.width} × ${outputSize.height}`
              : preset.guide}
          </p>
          {tooSmall && (
            <p className="text-[var(--color-warning)]">
              {`원본이 작아 권장 해상도(${preset.targetSize}×${preset.targetSize})보다 작게 저장됩니다.`}
            </p>
          )}
          {preset.hint && (
            <p className="text-[var(--color-text-muted)]">{preset.hint}</p>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={isRendering}
            onClick={() => finish([])}
          >
            전체 취소
          </Button>
          {hasQueue && (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={isRendering}
              onClick={skipRest}
            >
              모두 이대로
            </Button>
          )}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={isRendering || !naturalSize}
            onClick={() => {
              const area = fullArea();
              if (area) void commit(area);
            }}
          >
            건너뛰기
          </Button>
          <Button
            type="button"
            size="sm"
            disabled={isRendering || !croppedArea}
            onClick={() => croppedArea && void commit(croppedArea)}
          >
            {isRendering ? "처리 중…" : "적용"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

async function imageSize(file: File) {
  const objectUrl = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = objectUrl;
    await image.decode();
    return { width: image.naturalWidth, height: image.naturalHeight };
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
