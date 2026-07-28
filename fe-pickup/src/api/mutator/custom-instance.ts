import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
} from "axios";

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
