import { describe, expect, it } from "vitest";
import {
  assignPokemonAvatars,
  pokemonAvatarForKey,
  POKEMON_AVATAR_URLS,
} from "@/lib/pokemon-avatars";

describe("assignPokemonAvatars", () => {
  it("보이는 사용자에게 라운드로빈으로 겹치지 않는 아바타를 배정한다", () => {
    const assignments = assignPokemonAvatars(["user-a", "user-b", "user-c"]);

    expect([...assignments.values()]).toEqual(POKEMON_AVATAR_URLS.slice(0, 3));
    expect(new Set(assignments.values()).size).toBe(3);
  });

  it("목록 순서가 바뀌어도 기존 사용자의 아바타를 유지한다", () => {
    const firstAssignments = assignPokemonAvatars(["user-a", "user-b"]);
    const assignments = assignPokemonAvatars(["user-b", "user-a", "user-c"]);

    expect(assignments.get("user-a")).toBe(firstAssignments.get("user-a"));
    expect(
      new Set(["user-a", "user-b", "user-c"].map((key) => assignments.get(key)))
        .size,
    ).toBe(3);
  });
});

describe("pokemonAvatarForKey", () => {
  it("같은 키에는 항상 같은 포켓몬을 돌려준다", () => {
    const first = pokemonAvatarForKey("seller-42");
    const second = pokemonAvatarForKey("seller-42");

    expect(first).toBe(second);
    expect(POKEMON_AVATAR_URLS).toContain(first);
  });

  it("다른 키에는 대체로 다른 포켓몬을 돌려준다", () => {
    const results = new Set(
      ["seller-1", "seller-2", "seller-3", "seller-4"].map(pokemonAvatarForKey),
    );

    expect(results.size).toBeGreaterThan(1);
  });
});
