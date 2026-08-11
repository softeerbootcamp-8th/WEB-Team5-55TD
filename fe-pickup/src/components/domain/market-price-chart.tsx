import { useEffect, useMemo, useState } from "react";
import {
  findGradedCard,
  getGradePriceHistory,
  type GradePriceSeries,
} from "@/api/poketrace";

interface MarketPriceChartProps {
  cardName: string;
  setName?: string;
  cardNumber?: string;
  preferredAgency?: string;
  preferredScore?: string;
}

const WIDTH = 720;
const HEIGHT = 240;
const PADDING = { top: 16, right: 16, bottom: 28, left: 58 };

function tierLabel(tier: string) {
  return tier.replace(/_(\d)_([05])$/, " $1.$2").replace("_", " ");
}

function preferredTier(tiers: string[], agency?: string, score?: string) {
  const wanted = `${agency ?? ""}_${(score ?? "").replace(".", "_")}`;
  return tiers.find((tier) => tier === wanted) ?? tiers[0];
}

export function MarketPriceChart(props: MarketPriceChartProps) {
  const [cardId, setCardId] = useState("");
  const [tiers, setTiers] = useState<string[]>([]);
  const [selectedTier, setSelectedTier] = useState("");
  const [series, setSeries] = useState<GradePriceSeries | null>(null);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    findGradedCard(
      props.cardName,
      props.setName,
      props.cardNumber,
      controller.signal,
    )
      .then((card) => {
        const tier = preferredTier(
          card.tiers,
          props.preferredAgency,
          props.preferredScore,
        );
        setCardId(card.cardId);
        setTiers(card.tiers);
        setSelectedTier(tier);
      })
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setHidden(true);
        }
      });
    return () => controller.abort();
  }, [
    props.cardName,
    props.setName,
    props.cardNumber,
    props.preferredAgency,
    props.preferredScore,
  ]);

  useEffect(() => {
    if (!cardId || !selectedTier) return;
    const controller = new AbortController();
    getGradePriceHistory(cardId, selectedTier, controller.signal)
      .then(setSeries)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setHidden(true);
        }
      });
    return () => controller.abort();
  }, [cardId, selectedTier]);

  if (hidden || !series) return null;

  return (
    <section
      aria-labelledby="market-price-heading"
      className="rounded-[var(--radius-lg)] border border-border bg-card p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="market-price-heading" className="font-semibold">
            등급별 시세
          </h2>
          <p className="mt-1 text-xs text-[var(--color-text-muted)]">
            최근 90일 · PokeTrace/eBay · USD
          </p>
        </div>
        <div
          className="flex max-w-full gap-1 overflow-x-auto"
          aria-label="감정 등급"
        >
          {tiers.map((tier) => (
            <button
              key={tier}
              type="button"
              onClick={() => {
                setHidden(false);
                setSeries(null);
                setSelectedTier(tier);
              }}
              aria-pressed={selectedTier === tier}
              className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                selectedTier === tier
                  ? "bg-foreground text-background"
                  : "bg-[var(--color-surface-2)] text-[var(--color-text-sub)] hover:text-foreground"
              }`}
            >
              {tierLabel(tier)}
            </button>
          ))}
        </div>
      </div>
      <PriceSvg series={series} />
    </section>
  );
}

function PriceSvg({ series }: { series: GradePriceSeries }) {
  const chart = useMemo(() => {
    const values = series.points.map((point) => point.price);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const x = (index: number) =>
      PADDING.left +
      (index / Math.max(1, series.points.length - 1)) *
        (WIDTH - PADDING.left - PADDING.right);
    const y = (price: number) =>
      PADDING.top +
      ((max - price) / range) * (HEIGHT - PADDING.top - PADDING.bottom);
    return {
      min,
      max,
      path: series.points
        .map(
          (point, index) => `${index ? "L" : "M"}${x(index)},${y(point.price)}`,
        )
        .join(" "),
    };
  }, [series]);
  const first = series.points[0];
  const last = series.points.at(-1)!;

  return (
    <div className="mt-5 overflow-hidden">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`${tierLabel(series.tier)} 최근 90일 시세 차트`}
        className="h-auto w-full"
      >
        {[0, 0.5, 1].map((ratio) => {
          const y =
            PADDING.top + ratio * (HEIGHT - PADDING.top - PADDING.bottom);
          const value = chart.max - ratio * (chart.max - chart.min);
          return (
            <g key={ratio}>
              <line
                x1={PADDING.left}
                x2={WIDTH - PADDING.right}
                y1={y}
                y2={y}
                stroke="var(--color-border)"
              />
              <text
                x={PADDING.left - 8}
                y={y + 4}
                textAnchor="end"
                fontSize="11"
                fill="var(--color-text-muted)"
              >
                ${Math.round(value).toLocaleString()}
              </text>
            </g>
          );
        })}
        <path
          d={chart.path}
          fill="none"
          stroke="var(--color-buyer)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <text
          x={PADDING.left}
          y={HEIGHT - 6}
          fontSize="11"
          fill="var(--color-text-muted)"
        >
          {first.date.slice(5)}
        </text>
        <text
          x={WIDTH - PADDING.right}
          y={HEIGHT - 6}
          textAnchor="end"
          fontSize="11"
          fill="var(--color-text-muted)"
        >
          {last.date.slice(5)}
        </text>
      </svg>
      <div className="mt-2 flex items-end justify-between gap-4">
        <span className="text-xs text-[var(--color-text-muted)]">
          {tierLabel(series.tier)} 기준
        </span>
        <span className="font-num text-lg font-semibold">
          ${last.price.toLocaleString()}
        </span>
      </div>
    </div>
  );
}
