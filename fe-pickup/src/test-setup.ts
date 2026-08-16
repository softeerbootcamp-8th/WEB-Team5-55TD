import "@testing-library/jest-dom/vitest";

// jsdom은 ResizeObserver를 구현하지 않는다. 컨테이너 폭을 관찰해 반응형으로 그리는
// 컴포넌트(예: MarketPriceChart)를 렌더링할 수 있도록 콜백을 호출하지 않는 스텁을 둔다.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;

Object.defineProperty(URL, "createObjectURL", {
  writable: true,
  value: (value: Blob) => `blob:test-${value.size}`,
});
Object.defineProperty(URL, "revokeObjectURL", {
  writable: true,
  value: () => undefined,
});
