const TCGDEX_IMAGE_PREFIX = "https://assets.tcgdex.net/";
const HIGH_SUFFIX = "/high.webp";

export function cardThumbnailUrl(imageUrl?: string): string | undefined {
  if (
    !imageUrl?.startsWith(TCGDEX_IMAGE_PREFIX) ||
    !imageUrl.endsWith(HIGH_SUFFIX)
  ) {
    return imageUrl;
  }

  return `${imageUrl.slice(0, -HIGH_SUFFIX.length)}/low.webp`;
}
