import WebSocket from 'ws';
import {
  encodeConnectFrame,
  encodeDisconnectFrame,
  encodeSubscribeFrame,
  StompDecoder,
} from './stomp.mjs';

export class StompAuctionClient {
  #config;
  #sessionIndex;
  #auctionId;
  #onMessage;
  #webSocketFactory;
  #socket;
  #decoder = new StompDecoder();
  #heartbeatTimer;
  #isExpectedClose = false;
  #isConnected = false;

  constructor({
    config,
    sessionIndex,
    auctionId,
    onMessage,
    webSocketFactory = (url, protocol, options) => new WebSocket(url, protocol, options),
  }) {
    this.#config = config;
    this.#sessionIndex = sessionIndex;
    this.#auctionId = auctionId;
    this.#onMessage = onMessage;
    this.#webSocketFactory = webSocketFactory;
  }

  connect() {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.#socket?.terminate();
        reject(new Error(`STOMP 연결 시간이 초과됐습니다. session=${this.#sessionIndex}`));
      }, this.#config.connectTimeoutMillis);

      const socket = this.#webSocketFactory(this.#config.wsUrl, 'v12.stomp', {
        headers: { Origin: this.#config.origin },
        perMessageDeflate: false,
      });
      this.#socket = socket;

      socket.once('open', () => {
        socket.send(
          encodeConnectFrame(new URL(this.#config.wsUrl).host, this.#config.heartbeatMillis),
        );
      });
      socket.on('message', (data) => {
        let frames;
        try {
          frames = this.#decoder.push(data);
        } catch (error) {
          clearTimeout(timeout);
          reject(error);
          socket.terminate();
          return;
        }
        for (const frame of frames) {
          if (frame.command === 'CONNECTED' && !this.#isConnected) {
            this.#isConnected = true;
            socket.send(
              encodeSubscribeFrame(
                `sub-${this.#sessionIndex}`,
                `/topic/auctions/${this.#auctionId}`,
              ),
            );
            this.#startHeartbeat();
            clearTimeout(timeout);
            resolve();
          } else if (frame.command === 'MESSAGE') {
            this.#onMessage(this, frame);
          } else if (frame.command === 'ERROR') {
            clearTimeout(timeout);
            reject(new Error(`STOMP ERROR: ${frame.body}`));
            socket.terminate();
          }
        }
      });
      socket.once('error', (error) => {
        clearTimeout(timeout);
        if (!this.#isConnected) {
          reject(error);
        }
      });
    });
  }

  close(abrupt = false) {
    this.#isExpectedClose = true;
    clearInterval(this.#heartbeatTimer);
    if (!this.#socket || this.#socket.readyState === WebSocket.CLOSED) {
      return;
    }
    if (abrupt) {
      this.#socket.terminate();
      return;
    }
    if (this.#socket.readyState === WebSocket.OPEN) {
      this.#socket.send(encodeDisconnectFrame());
      this.#socket.close(1000, 'load test complete');
    }
  }

  onClose(listener) {
    this.#socket?.on('close', () => listener(this.#isExpectedClose));
  }

  get sessionIndex() {
    return this.#sessionIndex;
  }

  get auctionId() {
    return this.#auctionId;
  }

  #startHeartbeat() {
    this.#heartbeatTimer = setInterval(() => {
      if (this.#socket?.readyState === WebSocket.OPEN) {
        this.#socket.send('\n');
      }
    }, this.#config.heartbeatMillis);
  }
}
