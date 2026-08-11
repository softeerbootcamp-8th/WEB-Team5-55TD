import { readFile } from 'node:fs/promises';

export async function readJsonConfig(path) {
  if (!path) {
    throw new Error('설정 파일 경로가 필요합니다. --config <path>를 지정하세요.');
  }

  let raw;
  try {
    raw = await readFile(path, 'utf8');
  } catch (error) {
    throw new Error(`설정 파일을 읽지 못했습니다: ${path}`, { cause: error });
  }

  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(`설정 파일이 올바른 JSON이 아닙니다: ${path}`, { cause: error });
  }
}

export function parseConfigPath(argv) {
  const configIndex = argv.indexOf('--config');
  return configIndex === -1 ? undefined : argv[configIndex + 1];
}

export function hasFlag(argv, flag) {
  return argv.includes(flag);
}

export function requireLoadTestConfirmation(argv) {
  if (!hasFlag(argv, '--confirm-load-test')) {
    throw new Error('부하 발생을 확인하려면 --confirm-load-test를 지정하세요.');
  }
}

export function requireString(value, field) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`${field}는 비어 있지 않은 문자열이어야 합니다.`);
  }
  return value;
}

export function requirePositiveInteger(value, field) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${field}는 양의 정수여야 합니다.`);
  }
  return value;
}

export function requireNonNegativeInteger(value, field) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${field}는 0 이상의 정수여야 합니다.`);
  }
  return value;
}

export function requireRatio(value, field) {
  if (typeof value !== 'number' || value < 0 || value > 1) {
    throw new Error(`${field}는 0 이상 1 이하의 숫자여야 합니다.`);
  }
  return value;
}

export function requireUrl(value, field, protocols) {
  requireString(value, field);
  let url;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`${field}가 올바른 URL이 아닙니다.`, { cause: error });
  }
  if (!protocols.includes(url.protocol)) {
    throw new Error(`${field} protocol은 ${protocols.join(', ')} 중 하나여야 합니다.`);
  }
  return url.toString();
}

export function requireOrigin(value, field) {
  return new URL(requireUrl(value, field, ['http:', 'https:'])).origin;
}

export function requirePositiveIntegerArray(value, field) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error(`${field}는 하나 이상의 양의 정수를 가져야 합니다.`);
  }
  return value.map((item, index) => requirePositiveInteger(item, `${field}[${index}]`));
}
