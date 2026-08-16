/**
 * 회원 가입·수정 입력 정책. 서버의 MemberPolicy 와 같은 규칙이어야 한다.
 *
 * 로그인은 형식을 검사하지 않는다 — 정책을 바꾸기 전에 만들어진 계정도 그대로 로그인할 수 있어야 한다.
 */
const LOGIN_ID_PATTERN = /^[a-z][a-z0-9_-]{4,14}$/;
const PASSWORD_PATTERN =
  /^(?:(?=.*[A-Za-z])(?=.*\d)|(?=.*[A-Za-z])(?=.*[^A-Za-z\d\s])|(?=.*\d)(?=.*[^A-Za-z\d\s]))\S{8,16}$/;

export const NICKNAME_MIN_LENGTH = 2;
export const NICKNAME_MAX_LENGTH = 8;

export const LOGIN_ID_GUIDE =
  "영문 소문자로 시작하는 5~15자 (영문 소문자, 숫자, _, -)";
export const PASSWORD_GUIDE = "영문·숫자·특수문자 중 2가지 이상 조합 8~16자";
export const NICKNAME_GUIDE = `${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자`;

export function isValidLoginId(value: string): boolean {
  return LOGIN_ID_PATTERN.test(value);
}

export function isValidPassword(value: string): boolean {
  return PASSWORD_PATTERN.test(value);
}

export function isValidNickname(value: string): boolean {
  const trimmed = value.trim();
  return (
    trimmed.length >= NICKNAME_MIN_LENGTH &&
    trimmed.length <= NICKNAME_MAX_LENGTH
  );
}
