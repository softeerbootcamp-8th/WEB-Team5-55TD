export function frame(command, headers = {}, body = '') {
  const lines = [command];
  for (const [key, value] of Object.entries(headers)) lines.push(`${key}:${value}`);
  lines.push('', body);
  return `${lines.join('\n')}\0`;
}

export function connect(socket) {
  socket.send(frame('CONNECT', {
    'accept-version': '1.2',
    'heart-beat': '10000,10000',
  }));
}

export function subscribe(socket, auctionId) {
  socket.send(frame('SUBSCRIBE', {
    id: `sub-${auctionId}`,
    destination: `/topic/auctions/${auctionId}`,
    ack: 'auto',
  }));
}

export function parseFrame(raw) {
  const separator = raw.indexOf('\n\n');
  if (separator < 0) return null;
  const headerLines = raw.slice(0, separator).split('\n');
  const headers = {};
  for (const line of headerLines.slice(1)) {
    const index = line.indexOf(':');
    if (index > 0) headers[line.slice(0, index)] = line.slice(index + 1);
  }
  const body = raw.slice(separator + 2).replace(/\0$/, '');
  return { command: headerLines[0], headers, body };
}
