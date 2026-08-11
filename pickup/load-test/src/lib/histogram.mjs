const MAX_RECORDED_MILLIS = 60_000;

export class MillisecondHistogram {
  #buckets = new Uint32Array(MAX_RECORDED_MILLIS + 1);
  #count = 0;
  #sum = 0;
  #max = 0;

  record(value) {
    if (!Number.isFinite(value) || value < 0) {
      return;
    }
    const millis = Math.min(Math.round(value), MAX_RECORDED_MILLIS);
    this.#buckets[millis] += 1;
    this.#count += 1;
    this.#sum += value;
    this.#max = Math.max(this.#max, value);
  }

  merge(summary) {
    if (!Array.isArray(summary.buckets)) {
      throw new Error('병합할 histogram에 buckets가 없습니다.');
    }
    for (const [millis, count] of summary.buckets) {
      this.#buckets[millis] += count;
      this.#count += count;
      this.#sum += millis * count;
      this.#max = Math.max(this.#max, millis);
    }
  }

  summary({ includeBuckets = false } = {}) {
    const result = {
      count: this.#count,
      averageMillis: this.#count === 0 ? null : this.#sum / this.#count,
      maxMillis: this.#count === 0 ? null : this.#max,
      p50Millis: this.#percentile(0.5),
      p95Millis: this.#percentile(0.95),
      p99Millis: this.#percentile(0.99),
    };
    if (includeBuckets) {
      result.buckets = [];
      for (let millis = 0; millis < this.#buckets.length; millis += 1) {
        const count = this.#buckets[millis];
        if (count > 0) {
          result.buckets.push([millis, count]);
        }
      }
    }
    return result;
  }

  #percentile(percentile) {
    if (this.#count === 0) {
      return null;
    }
    const target = Math.ceil(this.#count * percentile);
    let accumulated = 0;
    for (let millis = 0; millis < this.#buckets.length; millis += 1) {
      accumulated += this.#buckets[millis];
      if (accumulated >= target) {
        return millis;
      }
    }
    return MAX_RECORDED_MILLIS;
  }
}
