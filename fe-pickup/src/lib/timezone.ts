/**
 * 서버는 항상 UTC로 통일하고, 클라이언트에서만 KST(Asia/Seoul)로 변환해 보여주거나
 * 입력을 받는다는 규약을 담는다. 한국은 DST가 없어 오프셋이 연중 +09:00로 고정이므로
 * 별도 타임존 라이브러리 없이 안전하게 계산할 수 있다.
 */
const KST_OFFSET_MILLIS = 9 * 60 * 60 * 1000;

/**
 * `<input type="datetime-local">`에서 나온 "YYYY-MM-DDTHH:mm" 값을, 그 값 자체를
 * KST 벽시계 시각으로 해석해 UTC ISO(Z 접미사) 문자열로 변환한다.
 *
 * 브라우저의 실제 로캘 타임존과 무관하게 항상 KST로 해석한다 — 이 앱의 사용자는
 * 자신의 OS 시계가 아니라 "한국 시각으로 몇 시"를 고르고 있다고 가정한다.
 */
export function kstLocalInputToUtcIso(localValue: string): string {
  const utcAsIfLocal = new Date(`${localValue}:00.000Z`);
  return new Date(utcAsIfLocal.getTime() - KST_OFFSET_MILLIS).toISOString();
}

/**
 * UTC instant를 `<input type="datetime-local">`이 사용하는 KST 벽시계 문자열로 바꾼다.
 * 브라우저의 getHours/getTimezoneOffset을 쓰지 않아 실행 환경의 타임존에 영향을 받지 않는다.
 */
export function utcInstantToKstLocalInput(instant: string | number): string {
  const epochMillis =
    typeof instant === "number" ? instant : new Date(instant).getTime();
  return new Date(epochMillis + KST_OFFSET_MILLIS).toISOString().slice(0, 16);
}

/**
 * `<input type="date">`용 "오늘" 값("YYYY-MM-DD", KST 벽시계 기준).
 * 브라우저 로캘 타임존과 무관하게 항상 KST 날짜로 계산한다 — 감정일처럼 미래를
 * 허용하지 않아야 하는 날짜 필드의 `max` 속성 및 검증에 쓴다.
 */
export function todayDateInputValue(): string {
  return new Date(Date.now() + KST_OFFSET_MILLIS).toISOString().slice(0, 10);
}
