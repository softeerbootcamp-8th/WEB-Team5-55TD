import { useSyncExternalStore } from "react";

/**
 * 로그인 여부 표시용 최소 상태.
 * 실제 세션은 HttpOnly 쿠키(access-token/refresh-token)가 담당하므로
 * JS 에서는 값을 읽을 수 없다. 이 플래그는 GNB 등 UI 전환만을 위한 것이다.
 */
const AUTH_KEY = "pickup:isAuthenticated";
/** 로그인 응답의 닉네임 — GNB MY 드롭다운 표시용. */
const NICKNAME_KEY = "pickup:nickname";

const listeners = new Set<() => void>();

function getSnapshot() {
  return localStorage.getItem(AUTH_KEY) === "true";
}

function getNicknameSnapshot() {
  return localStorage.getItem(NICKNAME_KEY);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function setAuthenticated(value: boolean) {
  if (value) {
    localStorage.setItem(AUTH_KEY, "true");
  } else {
    localStorage.removeItem(AUTH_KEY);
    localStorage.removeItem(NICKNAME_KEY);
  }
  listeners.forEach((listener) => listener());
}

export function setNickname(nickname: string) {
  localStorage.setItem(NICKNAME_KEY, nickname);
  listeners.forEach((listener) => listener());
}

export function useIsAuthenticated() {
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}

export function useNickname() {
  return useSyncExternalStore(subscribe, getNicknameSnapshot, () => null);
}
