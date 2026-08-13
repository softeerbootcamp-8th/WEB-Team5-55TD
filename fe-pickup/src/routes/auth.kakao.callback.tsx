import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { finishKakaoLogin } from "@/lib/kakao-auth";
import { setAuthenticated, setNickname } from "@/lib/auth";

export const Route = createFileRoute("/auth/kakao/callback")({ component: KakaoCallbackPage });

function KakaoCallbackPage() {
  const navigate = useNavigate();
  const params = new URLSearchParams(window.location.search);
  const code = params.get("code");
  const state = params.get("state");
  const [error, setError] = useState<string | undefined>(
    !code || !state ? "카카오 인증 응답이 올바르지 않습니다." : undefined,
  );
  useEffect(() => {
    if (!code || !state) return;
    finishKakaoLogin(code, state).then((member) => {
      setAuthenticated(true); setNickname(member.nickname); navigate({ to: "/home", replace: true });
    }).catch(() => setError("카카오 로그인에 실패했습니다. 다시 시도해 주세요."));
  }, [code, navigate, state]);
  return <main className="flex min-h-dvh items-center justify-center px-8"><p>{error ?? "카카오 로그인 처리 중…"}</p></main>;
}
