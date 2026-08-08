import "@testing-library/jest-dom/vitest";

Object.defineProperty(URL, "createObjectURL", {
  writable: true,
  value: (value: Blob) => `blob:test-${value.size}`,
});
Object.defineProperty(URL, "revokeObjectURL", {
  writable: true,
  value: () => undefined,
});
