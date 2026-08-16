import { axiosInstance } from "@/api/mutator/custom-instance";

export interface KakaoLoginResponse {
  memberId: number;
  loginId: string;
  nickname: string;
  profileImageUrl?: string;
  needsNickname: boolean;
}

export function startKakaoLogin() {
  const clientId = import.meta.env.VITE_KAKAO_CLIENT_ID;
  if (!clientId) throw new Error("카카오 로그인이 아직 설정되지 않았습니다.");
  const state = crypto.randomUUID();
  sessionStorage.setItem("kakao_oauth_state", state);
  const redirectUri = `${window.location.origin}/auth/kakao/callback`;
  const url = new URL("https://kauth.kakao.com/oauth/authorize");
  url.search = new URLSearchParams({ client_id: clientId, redirect_uri: redirectUri, response_type: "code", state }).toString();
  window.location.assign(url);
}

export async function finishKakaoLogin(code: string, state: string) {
  const expectedState = sessionStorage.getItem("kakao_oauth_state");
  sessionStorage.removeItem("kakao_oauth_state");
  if (!expectedState || state !== expectedState) throw new Error("유효하지 않은 로그인 요청입니다.");
  const redirectUri = `${window.location.origin}/auth/kakao/callback`;
  const { data } = await axiosInstance.post<KakaoLoginResponse>("/auth/kakao", { code, redirectUri });
  return data;
}
