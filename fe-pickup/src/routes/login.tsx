import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { login } from "@/api/generated/authentication/authentication";
import type { ExceptionResponse, LoginRequest } from "@/api/generated/model";
import { setAuthenticated, setNickname } from "@/lib/auth";
import { Logo } from "@/components/logo";

export const Route = createFileRoute("/login")({
  component: LoginPage,
});

const DEFAULT_ERROR_MESSAGE = "아이디 또는 비밀번호를 확인해 주세요.";

/** DESIGN.md · login.html — 아이디·비밀번호를 입력하면 활성. 형식 검증은 가입에서만 한다. */
function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const valid = username.trim().length > 0 && password.trim().length > 0;

  const { mutate, isPending } = useMutation({
    mutationFn: (loginRequest: LoginRequest) => login(loginRequest),
    onSuccess: (data) => {
      setAuthenticated(true);
      if (data.nickname) setNickname(data.nickname);
      navigate({ to: "/home" });
    },
    onError: (error: AxiosError<ExceptionResponse>) => {
      setErrorMessage(error.response?.data?.message ?? DEFAULT_ERROR_MESSAGE);
    },
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || isPending) return;
    mutate({ loginId: username.trim(), password });
  };

  return (
    <main
      data-role="buyer"
      className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-8 px-8"
    >
      <div className="flex flex-col items-center gap-2 text-center">
        <Link to="/home" className="flex flex-col items-center gap-2">
          <Logo role="buyer" className="size-8" />
          <h1 className="text-2xl font-bold">PickUp</h1>
        </Link>
        <p className="text-sm text-[var(--color-text-sub)]">
          피카! 맘에 드는 포켓몬카드 픽업!
        </p>
      </div>

      <form onSubmit={submit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="username">아이디</Label>
          <Input
            id="username"
            value={username}
            onChange={(e) => {
              setUsername(e.target.value);
              setErrorMessage(null);
            }}
            placeholder="아이디"
            autoComplete="username"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="password">비밀번호</Label>
          <Input
            id="password"
            type="password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              setErrorMessage(null);
            }}
            placeholder="비밀번호"
            autoComplete="current-password"
            aria-invalid={errorMessage !== null}
          />
          {errorMessage && (
            <p className="text-xs text-[var(--color-danger)]">{errorMessage}</p>
          )}
        </div>

        <Button
          type="submit"
          size="lg"
          disabled={!valid || isPending}
          className="mt-2 w-full"
        >
          {isPending ? "로그인 중..." : "로그인"}
        </Button>
      </form>

      <p className="text-center text-sm text-[var(--color-text-sub)]">
        아직 회원이 아니신가요?{" "}
        <Link
          to="/register"
          className="font-semibold text-primary hover:underline"
        >
          회원가입
        </Link>
      </p>
    </main>
  );
}
