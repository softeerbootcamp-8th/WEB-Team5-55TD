export const POKEMON_AVATAR_URLS = [
  "/avatars/pokemon/pikachu.webp",
  "/avatars/pokemon/squirtle.webp",
  "/avatars/pokemon/bulbasaur.webp",
  "/avatars/pokemon/charmander.webp",
  "/avatars/pokemon/eevee.webp",
  "/avatars/pokemon/ditto.webp",
  "/avatars/pokemon/jigglypuff.webp",
  "/avatars/pokemon/togepi.webp",
  "/avatars/pokemon/psyduck.webp",
  "/avatars/pokemon/snorlax.webp",
  "/avatars/pokemon/mew.webp",
  "/avatars/pokemon/vulpix.webp",
] as const;

/**
 * 현재 보이는 입찰자에게 아직 쓰이지 않은 포켓몬을 차례로 배정한다.
 * 배정 결과를 호출자가 보관하므로 목록 순서가 바뀌어도 같은 사용자의 아바타는 유지된다.
 */
export function assignPokemonAvatars(
  bidderKeys: string[],
): Map<string, string> {
  const stableKeys = [...new Set(bidderKeys)].sort((left, right) =>
    left.localeCompare(right),
  );

  return new Map(
    stableKeys.map((key, index) => [
      key,
      POKEMON_AVATAR_URLS[index % POKEMON_AVATAR_URLS.length],
    ]),
  );
}
