import { describe, expect, it, vi } from "vitest";
import { CreateImageUploadRequestPurpose } from "@/api/generated/model";
import { getImageValidationError, uploadImage } from "@/api/image-upload";

const { createUpload } = vi.hoisted(() => ({ createUpload: vi.fn() }));
vi.mock("@/api/generated/image-upload/image-upload", () => ({ createUpload }));

describe("image upload", () => {
  it("validates type, empty file and size boundaries", () => {
    expect(
      getImageValidationError(new File(["x"], "a.gif", { type: "image/gif" })),
    ).toContain("JPG");
    expect(
      getImageValidationError(
        new File([], "empty.jpg", { type: "image/jpeg" }),
      ),
    ).toContain("빈 파일");
    const huge = new File([new Uint8Array(10 * 1024 * 1024 + 1)], "huge.jpg", {
      type: "image/jpeg",
    });
    expect(getImageValidationError(huge)).toContain("10MB");
    expect(
      getImageValidationError(
        new File(["x"], "ok.jpg", { type: "image/jpeg" }),
      ),
    ).toBeNull();
  });

  it("requests a presigned upload and uploads the file", async () => {
    createUpload.mockResolvedValue({
      uploadUrl: "https://upload.test/card",
      temporaryObjectKey: "tmp/card",
      requiredHeaders: { "Content-Type": "image/jpeg" },
    });
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    const file = new File(["x"], "card.jpg", { type: "image/jpeg" });
    await expect(
      uploadImage(file, CreateImageUploadRequestPurpose.CONSIGNMENT),
    ).resolves.toBe("tmp/card");
    expect(fetchMock).toHaveBeenCalledWith(
      "https://upload.test/card",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("rejects incomplete upload metadata and failed upload responses", async () => {
    const file = new File(["x"], "card.jpg", { type: "image/jpeg" });
    createUpload.mockResolvedValueOnce({
      uploadUrl: "",
      temporaryObjectKey: "tmp",
    });
    await expect(
      uploadImage(file, CreateImageUploadRequestPurpose.CONSIGNMENT),
    ).rejects.toThrow("업로드 정보");
    createUpload.mockResolvedValueOnce({
      uploadUrl: "https://upload.test",
      temporaryObjectKey: "tmp",
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    await expect(
      uploadImage(file, CreateImageUploadRequestPurpose.CONSIGNMENT),
    ).rejects.toThrow("업로드에 실패");
  });
});
