import { AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import { afterEach, describe, expect, it } from "vitest";
import { attachCsrfHeader } from "./custom-instance";

function clearCookies() {
  document.cookie.split(";").forEach((entry) => {
    const name = entry.split("=")[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  });
}

function makeConfig(method: string): InternalAxiosRequestConfig {
  return { method, headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
}

describe("attachCsrfHeader", () => {
  afterEach(() => {
    clearCookies();
  });

  it("csrf-token 쿠키가 있으면 POST 요청에 헤더를 붙인다", () => {
    document.cookie = "csrf-token=abc123; path=/";

    const config = attachCsrfHeader(makeConfig("post"));

    expect(config.headers.get("X-CSRF-Token")).toBe("abc123");
  });

  it("csrf-token 쿠키가 없으면 헤더를 붙이지 않는다", () => {
    const config = attachCsrfHeader(makeConfig("post"));

    expect(config.headers.get("X-CSRF-Token")).toBeUndefined();
  });

  it("GET 요청에는 쿠키가 있어도 헤더를 붙이지 않는다", () => {
    document.cookie = "csrf-token=abc123; path=/";

    const config = attachCsrfHeader(makeConfig("get"));

    expect(config.headers.get("X-CSRF-Token")).toBeUndefined();
  });

  it("DELETE 요청에도 헤더를 붙인다", () => {
    document.cookie = "csrf-token=xyz789; path=/";

    const config = attachCsrfHeader(makeConfig("delete"));

    expect(config.headers.get("X-CSRF-Token")).toBe("xyz789");
  });
});
