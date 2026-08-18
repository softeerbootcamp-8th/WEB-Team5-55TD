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

/**
 * 판매자처럼 한 화면에 한 명만 나오는 대상에게 프로필 이미지가 없을 때 쓸
 * 포켓몬 아바타를 키(회원ID·닉네임 등) 해시로 정한다. 같은 키는 항상 같은
 * 포켓몬으로 고정되어(재렌더/새로고침에도 안 바뀜) 다른 키는 대체로 다른
 * 포켓몬이 나온다 — `assignPokemonAvatars`는 같은 화면에 여러 명이 겹치지
 * 않게 나눠줘야 할 때(입찰 목록) 쓰고, 이건 단일 대상용이다.
 */
export function pokemonAvatarForKey(key: string): string {
  let hash = 0;
  for (let i = 0; i < key.length; i++) {
    hash = (hash * 31 + key.charCodeAt(i)) % POKEMON_AVATAR_URLS.length;
  }
  return POKEMON_AVATAR_URLS[hash];
}
