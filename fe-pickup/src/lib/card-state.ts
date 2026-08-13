import { CardState } from "@/api/generated/model";

export const CARD_STATE_OPTIONS = [
  { value: CardState.HIGH, label: "상" },
  { value: CardState.MEDIUM, label: "중" },
  { value: CardState.LOW, label: "하" },
] as const;

const CARD_STATE_LABELS: Record<CardState, string> = {
  [CardState.HIGH]: "상",
  [CardState.MEDIUM]: "중",
  [CardState.LOW]: "하",
};

export function getCardStateLabel(cardState?: CardState): string {
  return cardState ? CARD_STATE_LABELS[cardState] : "-";
}
