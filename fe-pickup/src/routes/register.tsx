import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import type { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { createMember } from "@/api/generated/member/member";
import type { ExceptionResponse, MemberRequest } from "@/api/generated/model";

export const Route = createFileRoute("/register")({
  component: RegisterPage,
});

/** DESIGN.md · register.html — 아이디·닉네임·비밀번호 모두 4자 이상 */
function RegisterPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");

  const passwordMismatch =
    passwordConfirm.length > 0 && password !== passwordConfirm;

  const valid =
    username.length >= 4 &&
    nickname.length >= 4 &&
    password.length >= 4 &&
    password === passwordConfirm;

  const { mutate, isPending } = useMutation({
    mutationFn: (memberRequest: MemberRequest) => createMember(memberRequest),
    onSuccess: () => {
      navigate({ to: "/login" });
    },
    onError: (error: AxiosError<ExceptionResponse>) => {
      toast.error(
        error.response?.data?.message ??
          "회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      );
    },
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || isPending) return;
    mutate({ loginId: username, nickname, password });
  };

  return (
    <main
      data-role="buyer"
      className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-8 px-8 py-12"
    >
      <div className="flex flex-col gap-4">
        <Link to="/home" className="flex items-center gap-2 self-start">
          <span className="inline-block size-6 rounded-[6px] bg-primary" />
          <span className="text-lg font-bold">PickUp</span>
        </Link>
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">회원가입</h1>
          <p className="text-sm text-[var(--color-text-sub)]">
            <span className="text-[var(--color-danger)]">*</span> 표시는 필수
            항목입니다.
          </p>
        </div>
      </div>

      <form onSubmit={submit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="username">
            아이디 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="아이디 (4자 이상)"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="nickname">
            닉네임 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            id="nickname"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="닉네임 (4자 이상)"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="password">
            비밀번호 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호 (4자 이상)"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="passwordConfirm">
            비밀번호 확인 <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            id="passwordConfirm"
            type="password"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            placeholder="비밀번호를 한 번 더 입력해 주세요"
          />
          {passwordMismatch && (
            <p className="text-xs text-[var(--color-danger)]">
              비밀번호가 일치하지 않습니다.
            </p>
          )}
        </div>

        <Button
          type="submit"
          size="lg"
          disabled={!valid || isPending}
          className="mt-2 w-full"
        >
          {isPending ? "가입 중..." : "가입하기"}
        </Button>
      </form>

      <p className="text-center text-sm text-[var(--color-text-sub)]">
        이미 계정이 있으신가요?{" "}
        <Link
          to="/login"
          className="font-semibold text-primary hover:underline"
        >
          로그인
        </Link>
      </p>
    </main>
  );
}
