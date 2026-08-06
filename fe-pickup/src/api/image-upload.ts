import { createUpload } from "@/api/generated/image-upload/image-upload";
import { CreateImageUploadRequestPurpose } from "@/api/generated/model";

export const IMAGE_ACCEPT = "image/jpeg,image/png,image/webp";
export const MAX_IMAGE_SIZE = 10 * 1024 * 1024;

const ALLOWED_IMAGE_TYPES = new Set(IMAGE_ACCEPT.split(","));

export type ImagePurpose = CreateImageUploadRequestPurpose;

export function getImageValidationError(file: File) {
  if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
    return "JPG, PNG, WebP 이미지만 업로드할 수 있습니다.";
  }
  if (file.size === 0) {
    return "빈 파일은 업로드할 수 없습니다.";
  }
  if (file.size > MAX_IMAGE_SIZE) {
    return "이미지는 10MB 이하의 파일이어야 합니다.";
  }
  return null;
}

export async function uploadImage(file: File, purpose: ImagePurpose) {
  const validationError = getImageValidationError(file);
  if (validationError) throw new Error(validationError);

  const upload = await createUpload({
    purpose,
    contentType: file.type,
    contentLength: file.size,
  });

  if (!upload.uploadUrl || !upload.temporaryObjectKey) {
    throw new Error("이미지 업로드 정보를 받지 못했습니다.");
  }

  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    headers: upload.requiredHeaders,
    body: file,
  });

  if (!response.ok) {
    throw new Error("이미지 업로드에 실패했습니다.");
  }

  return upload.temporaryObjectKey;
}
