/**
 * 입찰 확인 다이얼로그의 "다시 보지 않기" 선택을 기기에 저장한다.
 * 로그아웃해도 유지되는 순수 UI 편의 설정이라 auth 상태와 무관하게 별도 키로 관리한다.
 */
const SKIP_BID_CONFIRM_KEY = "pickup:skipBidConfirm";

export function shouldSkipBidConfirm(): boolean {
  return localStorage.getItem(SKIP_BID_CONFIRM_KEY) === "true";
}

export function setSkipBidConfirm(value: boolean) {
  if (value) {
    localStorage.setItem(SKIP_BID_CONFIRM_KEY, "true");
  } else {
    localStorage.removeItem(SKIP_BID_CONFIRM_KEY);
  }
}
