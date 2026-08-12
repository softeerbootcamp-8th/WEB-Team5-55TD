import { describe, expect, it } from "vitest";
import {
  isValidLoginId,
  isValidNickname,
  isValidPassword,
} from "@/lib/member-policy";

describe("회원 입력 정책", () => {
  it("아이디는 영문 소문자로 시작하는 5~15자만 허용한다", () => {
    expect(isValidLoginId("pickup")).toBe(true);
    expect(isValidLoginId("pick_up-01")).toBe(true);
    expect(isValidLoginId("pick")).toBe(false); // 5자 미만
    expect(isValidLoginId("1pickup")).toBe(false); // 숫자로 시작
    expect(isValidLoginId("Pickup")).toBe(false); // 대문자
    expect(isValidLoginId("피카츄유저")).toBe(false); // 한글
    expect(isValidLoginId("p".repeat(15))).toBe(true);
    expect(isValidLoginId("p".repeat(16))).toBe(false); // 15자 초과
  });

  it("비밀번호는 두 종류 이상을 조합한 8~16자만 허용한다", () => {
    expect(isValidPassword("pickup12")).toBe(true);
    expect(isValidPassword("pickup!!")).toBe(true);
    expect(isValidPassword("12345678!")).toBe(true);
    expect(isValidPassword("pickup1")).toBe(false); // 8자 미만
    expect(isValidPassword("pickuppickup")).toBe(false); // 영문 한 종류
    expect(isValidPassword("12345678")).toBe(false); // 숫자 한 종류
    expect(isValidPassword("pickup 12")).toBe(false); // 공백
    expect(isValidPassword("pickup12".repeat(3))).toBe(false); // 16자 초과
  });

  it("닉네임은 2~8자를 허용한다", () => {
    expect(isValidNickname("가나")).toBe(true);
    expect(isValidNickname("ab")).toBe(true);
    expect(isValidNickname(" 가나 ")).toBe(true);
    expect(isValidNickname("가")).toBe(false);
    expect(isValidNickname(" ")).toBe(false);
    expect(isValidNickname("가".repeat(8))).toBe(true);
    expect(isValidNickname("가".repeat(9))).toBe(false);
  });
});
