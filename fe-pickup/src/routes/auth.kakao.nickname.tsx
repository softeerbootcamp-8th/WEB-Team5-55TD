import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { AxiosError } from "axios";
import { RefreshCw } from "lucide-react";
import { updateMyProfile } from "@/api/generated/member/member";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { isValidNickname, NICKNAME_GUIDE } from "@/lib/member-policy";
import { generateRandomNickname } from "@/lib/random-nickname";
import { setNickname } from "@/lib/auth";

export const Route = createFileRoute("/auth/kakao/nickname")({ component: KakaoNicknamePage });

function KakaoNicknamePage() {
  const navigate = useNavigate();
  const [nickname, setNicknameInput] = useState(
    () => sessionStorage.getItem("kakao_pending_nickname") ?? generateRandomNickname(),
  );
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    sessionStorage.setItem("kakao_pending_nickname", nickname);
  }, [nickname]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const value = nickname.trim();
    if (!isValidNickname(value) || saving) return;
    setSaving(true);
    setError(null);
    try {
      const profile = await updateMyProfile({ nickname: value });
      setNickname(profile.nickname ?? value);
      sessionStorage.removeItem("kakao_pending_nickname");
      navigate({ to: "/home", replace: true });
    } catch (requestError) {
      const status = requestError instanceof AxiosError ? requestError.response?.status : undefined;
      setError(status === 409 ? "이미 사용 중인 닉네임입니다. 다른 닉네임을 선택해 주세요." : "닉네임 저장에 실패했습니다. 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  };

  const valid = isValidNickname(nickname);
  return (
    <main className="flex min-h-dvh items-center justify-center px-6 py-12">
      <form onSubmit={submit} className="w-full max-w-sm space-y-6 rounded-2xl border bg-white p-6 shadow-sm">
        <div className="space-y-2 text-center">
          <h1 className="text-xl font-semibold">닉네임을 정해주세요</h1>
          <p className="text-sm text-[var(--color-text-sub)]">카카오 회원가입을 위한 닉네임입니다.</p>
        </div>
        <div className="space-y-2">
          <Label htmlFor="kakao-nickname">닉네임</Label>
          <div className="flex gap-2">
            <Input id="kakao-nickname" value={nickname} maxLength={8} onChange={(event) => setNicknameInput(event.target.value)} autoFocus />
            <Button type="button" variant="secondary" size="icon" onClick={() => setNicknameInput(generateRandomNickname())} aria-label="랜덤 닉네임 다시 생성">
              <RefreshCw className="size-4" />
            </Button>
          </div>
          <p className="text-xs text-[var(--color-text-sub)]">{NICKNAME_GUIDE}</p>
          {!valid && nickname.length > 0 && <p className="text-xs text-red-500">닉네임은 2~8자로 입력해 주세요.</p>}
          {error && <p className="text-xs text-red-500">{error}</p>}
        </div>
        <Button type="submit" className="w-full" disabled={!valid || saving}>{saving ? "저장 중…" : "시작하기"}</Button>
      </form>
    </main>
  );
}
