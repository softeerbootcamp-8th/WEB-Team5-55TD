import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export const Route = createFileRoute("/register")({
  component: RegisterPage,
});

const TAKEN_NICKNAMES = ["cardianKim", "admin", "pokemart"];

/** DESIGN.md · register.html — 모두 4자 이상, 닉네임 중복 확인 */
function RegisterPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [nickCheck, setNickCheck] = useState<"idle" | "ok" | "dup">("idle");

  const valid =
    username.length >= 4 &&
    nickname.length >= 4 &&
    password.length >= 4 &&
    nickCheck === "ok";

  const checkNickname = () => {
    if (nickname.length < 4) return;
    setNickCheck(TAKEN_NICKNAMES.includes(nickname) ? "dup" : "ok");
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    navigate({ to: "/login" });
  };

  return (
    <main
      data-role="buyer"
      className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-8 px-8 py-12"
    >
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">회원가입</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          <span className="text-[var(--color-danger)]">*</span> 표시는 필수
          항목입니다.
        </p>
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
          <div className="flex gap-2">
            <Input
              id="nickname"
              value={nickname}
              onChange={(e) => {
                setNickname(e.target.value);
                setNickCheck("idle");
              }}
              placeholder="닉네임 (4자 이상)"
              aria-invalid={nickCheck === "dup"}
            />
            <Button
              type="button"
              variant="secondary"
              onClick={checkNickname}
              disabled={nickname.length < 4}
            >
              중복 확인
            </Button>
          </div>
          {nickCheck === "dup" && (
            <p className="text-xs text-[var(--color-danger)]">
              닉네임은 중복할 수 없습니다.
            </p>
          )}
          {nickCheck === "ok" && (
            <p className="text-xs text-[var(--color-success)]">
              사용 가능한 닉네임입니다.
            </p>
          )}
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

        <Button
          type="submit"
          size="lg"
          disabled={!valid}
          className="mt-2 w-full"
        >
          가입하기
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
