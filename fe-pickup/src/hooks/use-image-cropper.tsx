import { useCallback, useRef, useState } from "react";
import type { ImagePurpose } from "@/api/image-upload";
import { ImageCropDialog } from "@/components/domain/image-crop-dialog";

/**
 * 파일을 고른 뒤 크롭 다이얼로그를 거쳐 최종 File 목록을 돌려준다.
 * 취소하면 빈 배열이므로 호출부는 아무것도 추가하지 않으면 된다.
 */
export function useImageCropper(purpose: ImagePurpose) {
  const [queue, setQueue] = useState<File[] | null>(null);
  const resolveRef = useRef<((files: File[]) => void) | null>(null);

  const requestCrop = useCallback((files: File[]) => {
    if (files.length === 0) return Promise.resolve<File[]>([]);
    return new Promise<File[]>((resolve) => {
      resolveRef.current = resolve;
      setQueue(files);
    });
  }, []);

  const finish = useCallback((rendered: File[]) => {
    setQueue(null);
    resolveRef.current?.(rendered);
    resolveRef.current = null;
  }, []);

  const cropper = queue ? (
    <ImageCropDialog
      key={queue.map((file) => `${file.name}-${file.lastModified}`).join("|")}
      files={queue}
      purpose={purpose}
      onFinish={finish}
    />
  ) : null;

  return { requestCrop, cropper };
}
