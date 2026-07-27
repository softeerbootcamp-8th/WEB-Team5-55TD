import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export const Route = createFileRoute("/login")({
  component: LoginPage,
});

/** DESIGN.md · login.html — 아이디·비밀번호 각 4자 이상 시 활성 */
function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(false);

  const valid = username.length >= 4 && password.length >= 4;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    // 목: 데모용 실패 계정
    if (username === "wrong") {
      setError(true);
      return;
    }
    navigate({ to: "/home" });
  };

  return (
    <main
      data-role="buyer"
      className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-8 px-8"
    >
      <div className="flex flex-col items-center gap-2 text-center">
        <span className="inline-block size-8 rounded-[8px] bg-primary" />
        <h1 className="text-2xl font-bold">카디언</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          검증된 카드, 안심 경매
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
              setError(false);
            }}
            placeholder="아이디 (4자 이상)"
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
              setError(false);
            }}
            placeholder="비밀번호 (4자 이상)"
            autoComplete="current-password"
            aria-invalid={error}
          />
          {error && (
            <p className="text-xs text-[var(--color-danger)]">
              아이디 또는 비밀번호를 확인해 주세요.
            </p>
          )}
        </div>

        <Button
          type="submit"
          size="lg"
          disabled={!valid}
          className="mt-2 w-full"
        >
          로그인
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
