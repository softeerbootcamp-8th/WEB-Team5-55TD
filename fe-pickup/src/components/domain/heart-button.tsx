import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useRouter } from "@tanstack/react-router";
import type { AxiosError } from "axios";
import { Heart } from "lucide-react";
import { toast } from "sonner";
import { useDeleteWatch, useRegisterWatch } from "@/api/generated/watch/watch";
import type { ExceptionResponse } from "@/api/generated/model";
import { useIsAuthenticated } from "@/lib/auth";
import { cn } from "@/lib/utils";

/** 관심(하트) 버튼 (DESIGN.md §5.3). 카드 우상단 배치용. */
export function HeartButton({
  active: controlledActive,
  defaultActive = false,
  count,
  className,
  isPending = false,
  onToggle,
}: {
  active?: boolean;
  defaultActive?: boolean;
  count?: number;
  className?: string;
  isPending?: boolean;
  onToggle?: (active: boolean) => void;
}) {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const [localActive, setLocalActive] = useState(defaultActive);
  const active = controlledActive ?? localActive;

  return (
    <button
      type="button"
      aria-pressed={active}
      aria-busy={isPending}
      aria-label={active ? "관심 해제" : "관심 등록"}
      disabled={isPending}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        if (!isAuthenticated) {
          toast.info("로그인 후 관심 경매를 등록할 수 있습니다.", {
            action: {
              label: "로그인",
              onClick: () => navigate({ to: "/login" }),
            },
          });
          return;
        }

        const nextActive = !active;
        if (controlledActive == null) setLocalActive(nextActive);
        onToggle?.(nextActive);
      }}
      className={cn(
        "inline-flex items-center gap-1 rounded-[var(--radius-pill)] bg-black/40 px-2 py-1 text-xs backdrop-blur-sm transition-colors disabled:cursor-wait disabled:opacity-70",
        active ? "text-primary" : "text-white/80 hover:text-white",
        className,
      )}
    >
      <Heart className={cn("size-4", active && "fill-current")} />
      {count != null && <span className="tabular">{count}</span>}
    </button>
  );
}

export function WatchButton({
  auctionId,
  watched,
  count,
  className,
}: {
  auctionId: string;
  watched: boolean;
  count?: number;
  className?: string;
}) {
  const navigate = useNavigate();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [optimisticWatch, setOptimisticWatch] = useState<{
    active: boolean;
    count?: number;
  } | null>(null);
  const active = optimisticWatch?.active ?? watched;
  const watchCount = optimisticWatch?.count ?? count;

  const registerMutation = useRegisterWatch();
  const deleteMutation = useDeleteWatch();
  const isPending = registerMutation.isPending || deleteMutation.isPending;

  const handleError = (error: unknown) => {
    const response = (error as AxiosError<ExceptionResponse>).response;
    if (response?.status === 401) {
      toast.error("로그인이 필요한 기능입니다.");
      navigate({ to: "/login" });
      return;
    }

    toast.error(
      response?.data?.message ??
        "관심 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  };

  const refreshAuctions = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["auctions"] }),
      queryClient.invalidateQueries({ queryKey: ["watchlist"] }),
    ]);
    await router.invalidate();
  };

  const toggleWatch = (nextActive: boolean) => {
    setOptimisticWatch({
      active: nextActive,
      count:
        watchCount == null
          ? undefined
          : Math.max(0, watchCount + (nextActive ? 1 : -1)),
    });

    const mutation = nextActive ? registerMutation : deleteMutation;
    mutation.mutate(
      { auctionId: Number(auctionId) },
      {
        onError: (error) => {
          setOptimisticWatch(null);
          handleError(error);
        },
        onSettled: async () => {
          await refreshAuctions();
          setOptimisticWatch(null);
        },
      },
    );
  };

  return (
    <HeartButton
      active={active}
      count={watchCount}
      className={className}
      isPending={isPending}
      onToggle={toggleWatch}
    />
  );
}
