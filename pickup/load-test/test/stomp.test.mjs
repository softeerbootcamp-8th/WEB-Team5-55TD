import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';
import { StompAuctionClient } from '../src/lib/stomp-client.mjs';
import { encodeFrame, StompDecoder } from '../src/lib/stomp.mjs';

test('STOMP decoder는 나뉜 frame과 합쳐진 frame을 처리한다', () => {
  const decoder = new StompDecoder();
  assert.deepEqual(decoder.push('CONNE'), []);
  const connectedAndMessage =
    'CTED\nversion:1.2\n\n\0\nMESSAGE\ndestination:/topic/auctions/42\n\n{"ok":true}\0';
  assert.deepEqual(decoder.push(connectedAndMessage), [
    { command: 'CONNECTED', headers: { version: '1.2' }, body: '' },
    {
      command: 'MESSAGE',
      headers: { destination: '/topic/auctions/42' },
      body: '{"ok":true}',
    },
  ]);
});

test('STOMP client는 CONNECTED 뒤 구독하고 MESSAGE를 전달한다', async () => {
  let socket;
  const message = new Promise((resolve) => {
    const client = new StompAuctionClient({
      config: {
        wsUrl: 'ws://test.example.com/ws',
        origin: 'https://test.example.com',
        heartbeatMillis: 10_000,
        connectTimeoutMillis: 1000,
      },
      sessionIndex: 0,
      auctionId: 42,
      onMessage: (source, frame) => {
        resolve({ source, frame });
        client.close();
      },
      webSocketFactory: () => {
        socket = new FakeWebSocket();
        queueMicrotask(() => socket.emit('open'));
        return socket;
      },
    });
    client.connect();
  });

  const received = await message;
  assert.equal(received.source.auctionId, 42);
  assert.equal(JSON.parse(received.frame.body).eventId, 'event-1');
});

class FakeWebSocket extends EventEmitter {
  readyState = 1;

  send(data) {
    if (data.startsWith('CONNECT')) {
      queueMicrotask(() => {
        this.emit('message', 'CONNE');
        this.emit('message', 'CTED\nversion:1.2\nheart-beat:10000,10000\n\n\0');
      });
    } else if (data.startsWith('SUBSCRIBE')) {
      queueMicrotask(() =>
        this.emit(
          'message',
          encodeFrame(
            'MESSAGE',
            { destination: '/topic/auctions/42' },
            JSON.stringify({ eventId: 'event-1' }),
          ),
        ),
      );
    }
  }

  close() {
    this.readyState = 3;
    this.emit('close');
  }

  terminate() {
    this.close();
  }
}
