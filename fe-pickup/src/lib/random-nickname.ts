const ADJECTIVES = ["용감한", "신나는", "상냥한", "재빠른", "영리한", "씩씩한", "행복한", "다정한"];
const POKEMON = ["피카츄", "꼬부기", "파이리", "이브이", "메타몽", "토게피", "리자몽", "잠만보"];

export function generateRandomNickname(): string {
  const adjective = ADJECTIVES[Math.floor(Math.random() * ADJECTIVES.length)];
  const pokemon = POKEMON[Math.floor(Math.random() * POKEMON.length)];
  const number = String(Math.floor(Math.random() * 100)).padStart(2, "0");
  return `${adjective}${pokemon}${number}`;
}
