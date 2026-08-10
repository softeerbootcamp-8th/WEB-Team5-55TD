/** 포맷 · 마스킹 유틸 (DESIGN.md §2, §6) */

/** 3자리 콤마 + '원' (DESIGN.md §3.2) */
export function formatWon(amount?: number): string {
  if (amount == null) return "-";
  return `${amount.toLocaleString("ko-KR")}원`;
}

/**
 * 억/만 단위로 축약한 금액 표기 (예: 1억 2,345만원, 128만원).
 * 큰 금액이 카드/박스 폭을 넘어서는 문제를 레이아웃이 아니라 자릿수 자체를
 * 줄여서 해결한다. 100만원 미만은 formatWon과 동일하게 전체 숫자를 그대로
 * 보여주고(작은 금액에서 만원 단위 절삭은 오차가 눈에 띄므로), 그 이상만
 * 축약하며 이때 만원 단위 미만 잔액은 버림(예: 123,456,789원 → "1억 2,345만원").
 * 정확한 금액이 필요한 표(입찰/판매 내역, 포인트 등)에는 쓰지 않는다.
 */
export function formatWonCompact(amount?: number): string {
  if (amount == null) return "-";
  const abs = Math.abs(amount);
  if (abs < 1_000_000) return formatWon(amount);
  const sign = amount < 0 ? "-" : "";
  const eok = Math.floor(abs / 100_000_000);
  const man = Math.floor((abs % 100_000_000) / 10_000);
  const parts: string[] = [];
  if (eok > 0) parts.push(`${eok.toLocaleString("ko-KR")}억`);
  if (man > 0) parts.push(`${man.toLocaleString("ko-KR")}만`);
  return `${sign}${parts.join(" ")}원`;
}

/** 포인트 표기: 1,280,000P */
export function formatPoint(amount?: number): string {
  if (amount == null) return "-";
  return `${amount.toLocaleString("ko-KR")}P`;
}

/** 남은 시간 → "HH : MM : SS" (DESIGN.md §5.5). 종료 시 "00 : 00 : 00" */
export function formatCountdown(msLeft: number): string {
  const clamped = Math.max(0, msLeft);
  const total = Math.floor(clamped / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(h)} : ${pad(m)} : ${pad(s)}`;
}

/** 절대 시각: 2026.07.22 15:00 */
export function formatDateTime(iso?: string): string {
  if (!iso) return "-";
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** 상대 시각: "방금 전", "1분 전", "3시간 전" (DESIGN.md §5.9) */
export function relativeTime(iso: string, now = Date.now()): string {
  const diff = now - new Date(iso).getTime();
  const sec = Math.floor(diff / 1000);
  if (sec < 10) return "방금 전";
  if (sec < 60) return `${sec}초 전`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  const day = Math.floor(hr / 24);
  return `${day}일 전`;
}

/**
 * 닉네임 마스킹: 앞 3글자 + *** + 뒤 2글자 (DESIGN.md §6, 예: bid***23)
 * 본인은 호출부에서 "나"로 대체한다.
 */
export function maskNickname(nickname: string): string {
  if (nickname.length <= 5) {
    const head = nickname.slice(0, Math.min(3, nickname.length));
    return `${head}***`;
  }
  return `${nickname.slice(0, 3)}***${nickname.slice(-2)}`;
}

/** 최소 입찰 단위 = 시작가의 5% (DESIGN.md §6) */
export function minBidUnit(startPrice: number): number {
  return Math.ceil((startPrice * 0.05) / 100) * 100;
}
