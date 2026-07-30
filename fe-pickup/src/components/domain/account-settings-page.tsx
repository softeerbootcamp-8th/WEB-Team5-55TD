import { useRef, useState } from "react";
import { Camera } from "lucide-react";
import { toast } from "sonner";
import { PageContainer } from "@/components/layout/page";
import { Avatar } from "@/components/domain/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { currentUser } from "@/lib/mock/data";

/** 계정 설정 — 프로필 이미지 · 닉네임 · 비밀번호 변경 (회원 정보 수정 API 연동 전 UI) */
export function AccountSettingsPage() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [avatarUrl, setAvatarUrl] = useState(currentUser.avatarUrl);
  const [nickname, setNickname] = useState(currentUser.nickname);
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");

  const nicknameValid = nickname.length >= 4;
  const passwordTouched =
    newPassword.length > 0 || newPasswordConfirm.length > 0;
  const passwordTooShort = passwordTouched && newPassword.length < 4;
  const passwordMismatch =
    passwordTouched && !passwordTooShort && newPassword !== newPasswordConfirm;
  const valid = nicknameValid && !passwordTooShort && !passwordMismatch;

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setAvatarUrl(reader.result as string);
    reader.readAsDataURL(file);
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    toast.success("계정 정보가 저장되었습니다.");
    setNewPassword("");
    setNewPasswordConfirm("");
  };

  return (
    <PageContainer className="flex max-w-lg flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">계정 설정</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          프로필 이미지·닉네임·비밀번호를 변경할 수 있습니다.
        </p>
      </div>

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
                  onClick={() => setAvatarUrl(undefined)}
                  className="text-xs text-[var(--color-text-muted)] hover:underline"
                >
                  기본 이미지로 되돌리기
                </button>
              )}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
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
                onChange={(e) => setNickname(e.target.value)}
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
                <Label htmlFor="newPassword">새 비밀번호</Label>
                <Input
                  id="newPassword"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="변경하지 않으려면 비워두세요"
                />
                {passwordTooShort && (
                  <p className="text-xs text-[var(--color-danger)]">
                    비밀번호는 4자 이상이어야 합니다.
                  </p>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="newPasswordConfirm">새 비밀번호 확인</Label>
                <Input
                  id="newPasswordConfirm"
                  type="password"
                  value={newPasswordConfirm}
                  onChange={(e) => setNewPasswordConfirm(e.target.value)}
                  placeholder="새 비밀번호를 한 번 더 입력해 주세요"
                />
                {passwordMismatch && (
                  <p className="text-xs text-[var(--color-danger)]">
                    비밀번호가 일치하지 않습니다.
                  </p>
                )}
              </div>
            </div>

            <Button
              type="submit"
              size="lg"
              disabled={!valid}
              className="w-full"
            >
              저장하기
            </Button>
          </form>
        </CardContent>
      </Card>
    </PageContainer>
  );
}
