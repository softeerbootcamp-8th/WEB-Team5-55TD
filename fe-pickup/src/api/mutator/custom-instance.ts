import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import { setAuthenticated } from "@/lib/auth";

/**
 * Orval 이 생성하는 API 클라이언트가 사용하는 커스텀 axios 인스턴스.
 * orval.config.ts 의 `override.mutator` 가 이 파일의 `customInstance` 를 가리킨다.
 */
export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
  headers: {
    "Content-Type": "application/json",
  },
  // 로그인 시 발급되는 access-token/refresh-token 이 HttpOnly 쿠키이므로 요청에 동봉한다.
  withCredentials: true,
});

// 로그인/토큰 갱신 자체에서 발생한 401 은 갱신 대상이 아니다 (자격 증명 불일치 · refresh-token 만료).
const SKIP_REFRESH_PATHS = new Set(["/auth", "/auth/refresh"]);
const REFRESHABLE_AUTH_ERRORS = new Set([
  "INVALID_ACCESS_TOKEN",
  "AUTHENTICATION_REQUIRED",
]);

const CSRF_COOKIE_NAME = "csrf-token";
const CSRF_HEADER_NAME = "X-CSRF-Token";
const SAFE_METHODS = new Set(["get", "head", "options"]);

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * 서버가 이중 제출 쿠키(csrf-token)로 CSRF 를 막으므로, 상태를 바꾸는 요청마다 같은 값을
 * 헤더에 실어 되돌려 보낸다. 로그인 전에는 쿠키 자체가 없어 자연히 생략된다.
 */
export function attachCsrfHeader(
  config: InternalAxiosRequestConfig,
): InternalAxiosRequestConfig {
  const method = config.method?.toLowerCase() ?? "get";
  if (SAFE_METHODS.has(method)) {
    return config;
  }
  const csrfToken = readCookie(CSRF_COOKIE_NAME);
  if (csrfToken) {
    config.headers.set(CSRF_HEADER_NAME, csrfToken);
  }
  return config;
}

axiosInstance.interceptors.request.use(attachCsrfHeader);

interface ApiErrorResponse {
  error?: string;
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retriedAfterRefresh?: boolean;
}

let refreshPromise: Promise<unknown> | null = null;

/**
 * access-token 을 갱신한다. 진행 중인 갱신이 있으면 그 프로미스를 그대로 반환해
 * 동시 호출(401 인터셉터 재시도 + 라우트 진입 시 선제 호출 등)이 중복 요청을 만들지 않게 한다.
 */
export function refreshAccessToken() {
  refreshPromise ??= axiosInstance.post("/auth/refresh").finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

// access-token 만료(401) 시 refresh-token 쿠키로 한 번 갱신을 시도한 뒤 원 요청을 재시도한다.
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryableRequestConfig | undefined;
    const errorCode = (error.response?.data as ApiErrorResponse | undefined)
      ?.error;

    if (
      error.response?.status !== 401 ||
      !errorCode ||
      !REFRESHABLE_AUTH_ERRORS.has(errorCode) ||
      !config ||
      config._retriedAfterRefresh ||
      SKIP_REFRESH_PATHS.has(config.url ?? "")
    ) {
      return Promise.reject(error);
    }

    config._retriedAfterRefresh = true;

    try {
      await refreshAccessToken();
      return axiosInstance(config);
    } catch {
      setAuthenticated(false);
      window.location.assign("/login");
      return Promise.reject(error);
    }
  },
);

export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  const source = axios.CancelToken.source();
  const promise = axiosInstance({
    ...config,
    ...options,
    cancelToken: source.token,
  }).then(({ data }: AxiosResponse<T>) => data);

  // TanStack Query 취소 연동
  // @ts-expect-error — cancel 메서드를 프로미스에 부착 (orval 규약)
  promise.cancel = () => source.cancel("Query was cancelled");

  return promise;
};

export type ErrorType<Error> = AxiosError<Error>;
export type BodyType<BodyData> = BodyData;
