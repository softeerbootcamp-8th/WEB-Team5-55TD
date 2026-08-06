import { useEffect, useId, useMemo } from "react";
import { ImagePlus, RefreshCw, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { IMAGE_ACCEPT, getImageValidationError } from "@/api/image-upload";

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

  return (
    <div className="flex flex-col gap-3">
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
              <div className="mt-auto flex gap-2">
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
        앞면·뒷면을 포함해 2~5장의 JPG, PNG, WebP 파일을 등록해 주세요. 파일당
        최대 10MB입니다.
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
