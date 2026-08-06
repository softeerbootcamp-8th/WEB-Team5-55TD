import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { Camera } from "lucide-react";
import { toast } from "sonner";
import {
  getGetMyProfileQueryKey,
  useGetMyProfile,
  useUpdateMyProfile,
} from "@/api/generated/member/member";
import type {
  ExceptionResponse,
  MyProfileResponse,
  UpdateMyProfileRequest,
} from "@/api/generated/model";
import {
  CreateImageUploadRequestPurpose,
  ProfileImageUpdateRequestAction,
} from "@/api/generated/model";
import {
  IMAGE_ACCEPT,
  getImageValidationError,
  uploadImage,
} from "@/api/image-upload";
import { Avatar } from "@/components/domain/avatar";
import { PageContainer } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { setNickname } from "@/lib/auth";

const PROFILE_ERROR_MESSAGE = "계정 정보를 불러오지 못했습니다.";
const UPDATE_ERROR_MESSAGE = "계정 정보를 저장하지 못했습니다.";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return (
    (error as AxiosError<ExceptionResponse> | undefined)?.response?.data
      ?.message ?? fallbackMessage
  );
}

/** 계정 설정 — 프로필 이미지 · 닉네임 · 비밀번호 변경 */
export function AccountSettingsPage() {
  const profileQuery = useGetMyProfile();
  const profile = profileQuery.data;

  return (
    <PageContainer className="flex max-w-lg flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">계정 설정</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          프로필 이미지·닉네임·비밀번호를 변경할 수 있습니다.
        </p>
      </div>

      {profileQuery.isLoading ? (
        <AccountSettingsState message="계정 정보를 불러오는 중입니다." />
      ) : profileQuery.isError ? (
        <AccountSettingsState
          message={getErrorMessage(profileQuery.error, PROFILE_ERROR_MESSAGE)}
          actionLabel="다시 시도"
          onAction={() => profileQuery.refetch()}
        />
      ) : !profile?.nickname ? (
        <AccountSettingsState
          message="표시할 계정 정보가 없습니다."
          actionLabel="다시 시도"
          onAction={() => profileQuery.refetch()}
        />
      ) : (
        <AccountSettingsForm
          key={`${profile.memberId ?? "me"}:${profile.nickname}:${profile.profileImageUrl ?? ""}`}
          profile={profile}
        />
      )}
    </PageContainer>
  );
}

function AccountSettingsState({
  message,
  actionLabel,
  onAction,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-4 p-6 text-center">
        <p className="text-sm text-[var(--color-text-sub)]">{message}</p>
        {actionLabel && onAction && (
          <Button type="button" variant="secondary" onClick={onAction}>
            {actionLabel}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

function AccountSettingsForm({ profile }: { profile: MyProfileResponse }) {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [avatarUrl, setAvatarUrl] = useState(profile.profileImageUrl);
  const [isAvatarChanged, setIsAvatarChanged] = useState(false);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [nickname, setNicknameInput] = useState(profile.nickname ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const nicknameValid = nickname.length >= 4;
  const passwordTouched =
    currentPassword.length > 0 ||
    newPassword.length > 0 ||
    newPasswordConfirm.length > 0;
  const currentPasswordTooShort = passwordTouched && currentPassword.length < 4;
  const newPasswordTooShort = passwordTouched && newPassword.length < 4;
  const passwordMismatch =
    passwordTouched &&
    !newPasswordTooShort &&
    newPassword !== newPasswordConfirm;
  const passwordValid =
    !passwordTouched ||
    (!currentPasswordTooShort && !newPasswordTooShort && !passwordMismatch);
  const nicknameChanged = nickname !== profile.nickname;
  const hasServerChanges = nicknameChanged || passwordTouched;
  const hasChanges = hasServerChanges || isAvatarChanged;
  const valid = nicknameValid && passwordValid && hasChanges;

  const updateProfileMutation = useUpdateMyProfile({
    mutation: {
      onSuccess: (updatedProfile) => {
        queryClient.setQueryData(getGetMyProfileQueryKey(), updatedProfile);
        if (updatedProfile.nickname) setNickname(updatedProfile.nickname);
        setAvatarUrl(updatedProfile.profileImageUrl);
        setAvatarFile(null);
        setIsAvatarChanged(false);
        if (fileInputRef.current) fileInputRef.current.value = "";
        setCurrentPassword("");
        setNewPassword("");
        setNewPasswordConfirm("");
        setErrorMessage(null);
        toast.success("계정 정보가 저장되었습니다.");
      },
      onError: (error: AxiosError<ExceptionResponse>) => {
        setErrorMessage(getErrorMessage(error, UPDATE_ERROR_MESSAGE));
      },
    },
  });
  const isSaving = isUploadingAvatar || updateProfileMutation.isPending;

  const handleAvatarChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const validationError = getImageValidationError(file);
    if (validationError) {
      toast.error(validationError);
      event.currentTarget.value = "";
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setAvatarUrl(reader.result as string);
      setAvatarFile(file);
      setIsAvatarChanged(true);
    };
    reader.readAsDataURL(file);
  };

  const resetAvatar = () => {
    setAvatarUrl(undefined);
    setAvatarFile(null);
    setIsAvatarChanged(true);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!valid || isSaving) return;

    const request: UpdateMyProfileRequest = {};
    if (nicknameChanged) request.nickname = nickname;
    if (passwordTouched) {
      request.currentPassword = currentPassword;
      request.password = newPassword;
    }

    if (isAvatarChanged) {
      if (avatarFile) {
        setIsUploadingAvatar(true);
        try {
          const temporaryObjectKey = await uploadImage(
            avatarFile,
            CreateImageUploadRequestPurpose.PROFILE,
          );
          request.profileImageUpdate = {
            action: ProfileImageUpdateRequestAction.SET,
            temporaryObjectKey,
          };
        } catch (error) {
          setErrorMessage(
            error instanceof Error ? error.message : UPDATE_ERROR_MESSAGE,
          );
          setIsUploadingAvatar(false);
          return;
        }
        setIsUploadingAvatar(false);
      } else {
        request.profileImageUpdate = {
          action: ProfileImageUpdateRequestAction.REMOVE,
        };
      }
    }

    updateProfileMutation.mutate({ data: request });
  };

  return (
    <Card>
      <CardContent className="flex flex-col gap-6 p-6">
        <div className="flex items-center gap-4">
          <Avatar src={avatarUrl} nickname={nickname} />
          <div className="flex flex-col items-start gap-1.5">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => fileInputRef.current?.click()}
            >
              <Camera /> 이미지 변경
            </Button>
            {avatarUrl && (
              <button
                type="button"
                onClick={resetAvatar}
                className="text-xs text-[var(--color-text-muted)] hover:underline"
              >
                기본 이미지로 되돌리기
              </button>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept={IMAGE_ACCEPT}
              className="hidden"
              onChange={handleAvatarChange}
            />
          </div>
        </div>

        <Separator />

        <form onSubmit={submit} className="flex flex-col gap-6">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="nickname">닉네임</Label>
            <Input
              id="nickname"
              value={nickname}
              onChange={(event) => {
                setNicknameInput(event.target.value);
                setErrorMessage(null);
              }}
              placeholder="닉네임 (4자 이상)"
            />
            {!nicknameValid && (
              <p className="text-xs text-[var(--color-danger)]">
                닉네임은 4자 이상이어야 합니다.
              </p>
            )}
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="currentPassword">현재 비밀번호</Label>
              <Input
                id="currentPassword"
                type="password"
                value={currentPassword}
                onChange={(event) => {
                  setCurrentPassword(event.target.value);
                  setErrorMessage(null);
                }}
                placeholder="비밀번호 변경 시 입력해 주세요"
                autoComplete="current-password"
              />
              {currentPasswordTooShort && (
                <p className="text-xs text-[var(--color-danger)]">
                  현재 비밀번호는 4자 이상 입력해 주세요.
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="newPassword">새 비밀번호</Label>
              <Input
                id="newPassword"
                type="password"
                value={newPassword}
                onChange={(event) => {
                  setNewPassword(event.target.value);
                  setErrorMessage(null);
                }}
                placeholder="변경하지 않으려면 비워두세요"
                autoComplete="new-password"
              />
              {newPasswordTooShort && (
                <p className="text-xs text-[var(--color-danger)]">
                  새 비밀번호는 4자 이상이어야 합니다.
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="newPasswordConfirm">새 비밀번호 확인</Label>
              <Input
                id="newPasswordConfirm"
                type="password"
                value={newPasswordConfirm}
                onChange={(event) => {
                  setNewPasswordConfirm(event.target.value);
                  setErrorMessage(null);
                }}
                placeholder="새 비밀번호를 한 번 더 입력해 주세요"
                autoComplete="new-password"
                aria-invalid={passwordMismatch}
              />
              {passwordMismatch && (
                <p className="text-xs text-[var(--color-danger)]">
                  새 비밀번호가 일치하지 않습니다.
                </p>
              )}
            </div>
          </div>

          {errorMessage && (
            <p className="text-sm text-[var(--color-danger)]">{errorMessage}</p>
          )}

          <Button
            type="submit"
            size="lg"
            disabled={!valid || isSaving}
            className="w-full"
          >
            {isSaving ? "저장 중..." : "저장하기"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
