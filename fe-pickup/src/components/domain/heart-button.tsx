import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useRouter } from "@tanstack/react-router";
import type { AxiosError } from "axios";
import { Heart } from "lucide-react";
import { toast } from "sonner";
import { useDeleteWatch, useRegisterWatch } from "@/api/generated/watch/watch";
import { getGetMyWatchesQueryKey } from "@/api/generated/member/member";
import type { ExceptionResponse } from "@/api/generated/model";
import { useIsAuthenticated } from "@/lib/auth";
import { cn } from "@/lib/utils";
import { applyWatchToAuctionCache, type WatchState } from "@/lib/watch-cache";

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

const CONFLICT_STATUS = 409;

function isAlreadyWatchedError(error: unknown) {
  return (
    (error as AxiosError<ExceptionResponse>).response?.status ===
    CONFLICT_STATUS
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

  // 경매 목록은 즉시 다시 불러오지 않는다 — 인기순 정렬이 그 자리에서 바뀌어
  // 방금 관심 등록한 카드가 다른 위치로 튀기 때문이다(OOTD-488). 캐시의 관심
  // 상태만 갈아끼우고, 순서는 목록을 다시 열거나 창에 돌아올 때 갱신되게 둔다.
  const refreshAuctions = async (state: WatchState) => {
    queryClient.setQueriesData({ queryKey: ["auctions"] }, (data) =>
      applyWatchToAuctionCache(data, auctionId, state),
    );
    queryClient.invalidateQueries({
      queryKey: ["auctions"],
      refetchType: "none",
    });
    await queryClient.invalidateQueries({
      queryKey: getGetMyWatchesQueryKey(),
    });
    await router.invalidate();
  };

  const toggleWatch = (nextActive: boolean) => {
    const previousState: WatchState = {
      watched: active,
      watchCount,
    };
    const nextCount =
      watchCount == null
        ? undefined
        : Math.max(0, watchCount + (nextActive ? 1 : -1));
    setOptimisticWatch({ active: nextActive, count: nextCount });

    const mutation = nextActive ? registerMutation : deleteMutation;
    let failed = false;
    mutation.mutate(
      { auctionId: Number(auctionId) },
      {
        onError: (error) => {
          // 등록 중복(409)은 서버에 이미 관심이 등록된 상태라 화면과 어긋나지 않는다.
          if (nextActive && isAlreadyWatchedError(error)) return;
          failed = true;
          setOptimisticWatch(null);
          handleError(error);
        },
        onSettled: async () => {
          await refreshAuctions(
            failed
              ? previousState
              : { watched: nextActive, watchCount: nextCount },
          );
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
