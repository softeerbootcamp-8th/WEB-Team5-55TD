import { useEffect, useId, useMemo } from "react";
import { Check, CircleDashed, ImagePlus, RefreshCw, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { IMAGE_ACCEPT, getImageValidationError } from "@/api/image-upload";
import { cn } from "@/lib/utils";

const MAX_IMAGES = 5;

export type ConsignmentImageValue =
  | {
      kind: "existing";
      consignmentImageId: number;
      imageUrl: string;
    }
  | {
      kind: "new";
      file: File;
    };

interface ConsignmentImageFieldsProps {
  images: ConsignmentImageValue[];
  onChange: (images: ConsignmentImageValue[]) => void;
  onError: (message: string) => void;
  disabled?: boolean;
}

export function ConsignmentImageFields({
  images,
  onChange,
  onError,
  disabled,
}: ConsignmentImageFieldsProps) {
  const inputIdPrefix = useId();

  const addImages = (files: File[]) => {
    if (images.length + files.length > MAX_IMAGES) {
      onError("상품 이미지는 최대 5장까지 등록할 수 있습니다.");
      return;
    }

    const validationError = files
      .map(getImageValidationError)
      .find((message) => message !== null);
    if (validationError) {
      onError(validationError);
      return;
    }

    onChange([
      ...images,
      ...files.map((file): ConsignmentImageValue => ({ kind: "new", file })),
    ]);
  };

  const replaceImage = (index: number, file: File) => {
    const validationError = getImageValidationError(file);
    if (validationError) {
      onError(validationError);
      return;
    }

    onChange(
      images.map((image, imageIndex) =>
        imageIndex === index ? { kind: "new", file } : image,
      ),
    );
  };

  const removeImage = (index: number) => {
    onChange(images.filter((_, imageIndex) => imageIndex !== index));
  };

  const hasFront = images.length >= 1;
  const hasBack = images.length >= 2;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-3 rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-4 text-xs text-[var(--color-text-sub)]">
        <p>
          카드의{" "}
          <strong className="font-semibold text-foreground">앞면</strong>과{" "}
          <strong className="font-semibold text-foreground">뒷면</strong>{" "}
          사진을 모두 첨부해 주세요. 처음 등록하는 두 장이 순서대로 앞면·뒷면
          이미지로 저장됩니다.
        </p>
        <div className="flex gap-6">
          <div className="flex flex-col items-center gap-1.5">
            <CardFaceDiagram variant="front" />
            <ImageChecklistItem label="앞면 사진" done={hasFront} />
          </div>
          <div className="flex flex-col items-center gap-1.5">
            <CardFaceDiagram variant="back" />
            <ImageChecklistItem label="뒷면 사진" done={hasBack} />
          </div>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {images.map((image, index) => (
          <div
            key={
              image.kind === "existing"
                ? `existing-${image.consignmentImageId}`
                : `${image.file.name}-${image.file.lastModified}-${index}`
            }
            className="flex gap-3 rounded-[var(--radius-md)] border border-border bg-[var(--color-surface-2)] p-3"
          >
            <ImagePreview image={image} label={getImageLabel(index)} />
            <div className="flex min-w-0 flex-1 flex-col gap-2">
              <div>
                <p className="text-sm font-semibold">{getImageLabel(index)}</p>
                <p className="truncate text-xs text-[var(--color-text-muted)]">
                  {image.kind === "new" ? image.file.name : "등록된 이미지"}
                </p>
              </div>
              {/* 교체/삭제 버튼은 둘 다 shrink-0(Button 기본값)이라 좁은 컬럼에서
                  줄어들지 못하고 박스 밖으로 삐져나갈 수 있다 — flex-wrap으로
                  공간이 부족하면 다음 줄로 떨어지게 한다. */}
              <div className="mt-auto flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  disabled={disabled}
                  asChild
                >
                  <label
                    htmlFor={`${inputIdPrefix}-replace-${index}`}
                    className="cursor-pointer"
                  >
                    <RefreshCw /> 교체
                    <input
                      id={`${inputIdPrefix}-replace-${index}`}
                      type="file"
                      accept={IMAGE_ACCEPT}
                      className="sr-only"
                      disabled={disabled}
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        if (file) replaceImage(index, file);
                        event.currentTarget.value = "";
                      }}
                    />
                  </label>
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={disabled}
                  onClick={() => removeImage(index)}
                >
                  <Trash2 /> 삭제
                </Button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {images.length < MAX_IMAGES && (
        <Button type="button" variant="secondary" disabled={disabled} asChild>
          <label htmlFor={`${inputIdPrefix}-add`} className="cursor-pointer">
            <ImagePlus /> 이미지 추가
            <input
              id={`${inputIdPrefix}-add`}
              type="file"
              accept={IMAGE_ACCEPT}
              multiple
              className="sr-only"
              disabled={disabled}
              onChange={(event) => {
                addImages(Array.from(event.target.files ?? []));
                event.currentTarget.value = "";
              }}
            />
          </label>
        </Button>
      )}

      <p className="text-xs text-[var(--color-text-muted)]">
        2~5장의 JPG, PNG, WebP 파일을 등록해 주세요. 파일당 최대 10MB입니다.
      </p>
    </div>
  );
}

function ImagePreview({
  image,
  label,
}: {
  image: ConsignmentImageValue;
  label: string;
}) {
  if (image.kind === "existing") {
    return <PreviewImage imageUrl={image.imageUrl} label={label} />;
  }

  return <NewImagePreview file={image.file} label={label} />;
}

function NewImagePreview({ file, label }: { file: File; label: string }) {
  const objectUrl = useMemo(() => URL.createObjectURL(file), [file]);

  useEffect(() => {
    return () => URL.revokeObjectURL(objectUrl);
  }, [objectUrl]);

  return <PreviewImage imageUrl={objectUrl} label={label} />;
}

function PreviewImage({
  imageUrl,
  label,
}: {
  imageUrl: string;
  label: string;
}) {
  return (
    <div className="size-24 shrink-0 overflow-hidden rounded-[var(--radius-sm)] bg-muted">
      <img
        src={imageUrl}
        alt={`${label} 미리보기`}
        className="size-full object-cover"
      />
    </div>
  );
}

function getImageLabel(index: number) {
  if (index === 0) return "앞면 이미지";
  if (index === 1) return "뒷면 이미지";
  return `추가 이미지 ${index - 1}`;
}

/**
 * 앞면/뒷면이 각각 어떤 사진인지 한눈에 보여주는 작은 다이어그램.
 * 앞면은 그림·이름표가 있는 카드, 뒷면은 균일한 패턴만 있는 카드로 단순화해 그린다
 * (실제 포켓몬 카드 뒷면 디자인을 그대로 재현하지 않는다).
 */
function CardFaceDiagram({ variant }: { variant: "front" | "back" }) {
  return (
    <svg
      viewBox="0 0 40 56"
      className="h-14 w-10 shrink-0 text-[var(--color-text-muted)]"
      aria-hidden
    >
      <rect
        x="1.5"
        y="1.5"
        width="37"
        height="53"
        rx="4"
        fill="var(--color-card)"
        stroke="currentColor"
        strokeWidth="2"
      />
      {variant === "front" ? (
        <>
          <rect
            x="6"
            y="6"
            width="28"
            height="26"
            rx="2"
            fill="currentColor"
            opacity="0.12"
          />
          <circle cx="15" cy="15" r="3" fill="currentColor" opacity="0.4" />
          <path
            d="M8 28 L17 17 L23 24 L29 14 L33 28 Z"
            fill="currentColor"
            opacity="0.4"
          />
          <rect
            x="6"
            y="37"
            width="28"
            height="4"
            rx="1.5"
            fill="currentColor"
            opacity="0.3"
          />
          <rect
            x="6"
            y="44"
            width="18"
            height="3"
            rx="1.5"
            fill="currentColor"
            opacity="0.2"
          />
        </>
      ) : (
        <>
          <rect
            x="6"
            y="6"
            width="28"
            height="44"
            rx="2"
            fill="currentColor"
            opacity="0.1"
          />
          <circle
            cx="20"
            cy="28"
            r="10"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            opacity="0.5"
          />
          <line
            x1="10"
            y1="28"
            x2="30"
            y2="28"
            stroke="currentColor"
            strokeWidth="2"
            opacity="0.5"
          />
          <circle cx="20" cy="28" r="2.5" fill="currentColor" opacity="0.5" />
        </>
      )}
    </svg>
  );
}

function ImageChecklistItem({
  label,
  done,
}: {
  label: string;
  done: boolean;
}) {
  return (
    <span
      className={cn(
        "flex items-center gap-1 font-medium",
        done ? "text-[var(--color-success)]" : "text-[var(--color-text-muted)]",
      )}
    >
      {done ? (
        <Check className="size-3.5" />
      ) : (
        <CircleDashed className="size-3.5" />
      )}
      {label}
    </span>
  );
}
