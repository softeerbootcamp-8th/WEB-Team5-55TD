import { useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { RefreshCw } from "lucide-react";
import {
  getGetMyProfileQueryKey,
  useGetMyProfile,
  useUpdateMyProfile,
} from "@/api/generated/member/member";
import type { ExceptionResponse, MyProfileResponse } from "@/api/generated/model";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/logo";
import { isValidNickname, NICKNAME_GUIDE } from "@/lib/member-policy";
import { generateRandomNickname } from "@/lib/random-nickname";
import { setNickname } from "@/lib/auth";

export const Route = createFileRoute("/auth/kakao/nickname")({
  component: KakaoNicknamePage,
});

const PROFILE_ERROR_MESSAGE = "회원 정보를 불러오지 못했습니다.";
const SAVE_ERROR_MESSAGE = "닉네임 저장에 실패했습니다. 다시 시도해 주세요.";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return (
    (error as AxiosError<ExceptionResponse> | undefined)?.response?.data
      ?.message ?? fallbackMessage
  );
}

function KakaoNicknamePage() {
  const profileQuery = useGetMyProfile();
  const profile = profileQuery.data;

  return (
    <main
      data-role="buyer"
      className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-8 px-8"
    >
      <div className="flex flex-col items-center gap-2 text-center">
        <Logo role="buyer" className="size-8" />
        <h1 className="text-2xl font-bold">닉네임을 정해주세요</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          카카오 회원가입을 완료하려면 닉네임이 필요해요.
        </p>
      </div>

      {profileQuery.isLoading ? (
        <p className="text-center text-sm text-[var(--color-text-sub)]">
          회원 정보를 불러오는 중입니다.
        </p>
      ) : profileQuery.isError || !profile?.nickname ? (
        <div className="flex flex-col items-center gap-4 text-center">
          <p className="text-sm text-[var(--color-text-sub)]">
            {getErrorMessage(profileQuery.error, PROFILE_ERROR_MESSAGE)}
          </p>
          <Button
            type="button"
            variant="secondary"
            onClick={() => profileQuery.refetch()}
          >
            다시 시도
          </Button>
        </div>
      ) : (
        <NicknameForm profile={profile} />
      )}
    </main>
  );
}

function NicknameForm({ profile }: { profile: MyProfileResponse }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [nickname, setNicknameInput] = useState(profile.nickname ?? "");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const normalizedNickname = nickname.trim();
  const nicknameInvalid =
    normalizedNickname.length > 0 && !isValidNickname(normalizedNickname);
  const valid = isValidNickname(normalizedNickname);

  const updateProfileMutation = useUpdateMyProfile({
    mutation: {
      onSuccess: (updatedProfile) => {
        queryClient.setQueryData(getGetMyProfileQueryKey(), updatedProfile);
        setNickname(updatedProfile.nickname ?? normalizedNickname);
        navigate({ to: "/home", replace: true });
      },
      onError: (error: AxiosError<ExceptionResponse>) => {
        setErrorMessage(getErrorMessage(error, SAVE_ERROR_MESSAGE));
      },
    },
  });

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!valid || updateProfileMutation.isPending) return;
    setErrorMessage(null);
    updateProfileMutation.mutate({
      data: { nickname: normalizedNickname },
    });
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="kakao-nickname">닉네임</Label>
        <div className="flex gap-2">
          <Input
            id="kakao-nickname"
            value={nickname}
            maxLength={8}
            onChange={(event) => {
              setNicknameInput(event.target.value);
              setErrorMessage(null);
            }}
            autoFocus
            aria-invalid={nicknameInvalid || errorMessage !== null}
          />
          <Button
            type="button"
            variant="secondary"
            size="icon"
            onClick={() => setNicknameInput(generateRandomNickname())}
            aria-label="랜덤 닉네임 다시 생성"
          >
            <RefreshCw className="size-4" />
          </Button>
        </div>
        <p
          className={
            nicknameInvalid
              ? "text-xs text-[var(--color-danger)]"
              : "text-xs text-[var(--color-text-muted)]"
          }
        >
          {NICKNAME_GUIDE}
        </p>
        {errorMessage && (
          <p className="text-xs text-[var(--color-danger)]">{errorMessage}</p>
        )}
      </div>

      <Button
        type="submit"
        size="lg"
        disabled={!valid || updateProfileMutation.isPending}
        className="mt-2 w-full"
      >
        {updateProfileMutation.isPending ? "저장 중..." : "시작하기"}
      </Button>
    </form>
  );
}
