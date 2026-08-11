export class SequenceBitSet {
  #base;
  #size;
  #words;
  #received = 0;

  constructor(base, size) {
    if (!Number.isSafeInteger(base) || !Number.isSafeInteger(size) || size <= 0) {
      throw new Error('SequenceBitSet의 base와 size가 올바르지 않습니다.');
    }
    this.#base = base;
    this.#size = size;
    this.#words = new Uint32Array(Math.ceil(size / 32));
  }

  add(sequence) {
    const offset = sequence - this.#base;
    if (!Number.isSafeInteger(sequence) || offset < 0 || offset >= this.#size) {
      return 'out_of_range';
    }
    const wordIndex = Math.floor(offset / 32);
    const mask = 1 << (offset % 32);
    if ((this.#words[wordIndex] & mask) !== 0) {
      return 'duplicate';
    }
    this.#words[wordIndex] |= mask;
    this.#received += 1;
    return 'added';
  }

  get received() {
    return this.#received;
  }
}
