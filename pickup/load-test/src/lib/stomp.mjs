const NULL_BYTE = '\0';

export function encodeConnectFrame(host, heartbeatMillis) {
  return encodeFrame('CONNECT', {
    'accept-version': '1.2',
    host,
    'heart-beat': `${heartbeatMillis},${heartbeatMillis}`,
  });
}

export function encodeSubscribeFrame(subscriptionId, destination) {
  return encodeFrame('SUBSCRIBE', {
    id: subscriptionId,
    destination,
    ack: 'auto',
  });
}

export function encodeDisconnectFrame() {
  return encodeFrame('DISCONNECT');
}

export function encodeFrame(command, headers = {}, body = '') {
  const headerLines = Object.entries(headers).map(([name, value]) => `${name}:${value}`);
  return `${command}\n${headerLines.join('\n')}\n\n${body}${NULL_BYTE}`;
}

export class StompDecoder {
  #buffer = '';

  push(chunk) {
    this.#buffer += Buffer.isBuffer(chunk) ? chunk.toString('utf8') : String(chunk);
    const frames = [];

    while (true) {
      this.#buffer = this.#buffer.replace(/^[\r\n]+/, '');
      const frameEnd = this.#buffer.indexOf(NULL_BYTE);
      if (frameEnd === -1) {
        break;
      }
      const rawFrame = this.#buffer.slice(0, frameEnd);
      this.#buffer = this.#buffer.slice(frameEnd + 1);
      if (rawFrame.trim() !== '') {
        frames.push(parseFrame(rawFrame));
      }
    }

    return frames;
  }
}

function parseFrame(rawFrame) {
  const normalized = rawFrame.replace(/\r\n/g, '\n');
  const headerEnd = normalized.indexOf('\n\n');
  if (headerEnd === -1) {
    throw new Error('STOMP frame에 header/body 구분자가 없습니다.');
  }
  const headerBlock = normalized.slice(0, headerEnd);
  const body = normalized.slice(headerEnd + 2);
  const [command, ...headerLines] = headerBlock.split('\n');
  const headers = {};
  for (const line of headerLines) {
    const separator = line.indexOf(':');
    if (separator === -1) {
      throw new Error(`올바르지 않은 STOMP header입니다: ${line}`);
    }
    headers[line.slice(0, separator)] = line.slice(separator + 1);
  }
  return { command, headers, body };
}
