import { Link, useNavigate } from "@tanstack/react-router";
import {
  ChevronDown,
  Gavel,
  Heart,
  LogOut,
  Menu,
  Package,
  Receipt,
  Repeat,
  Settings,
  User as UserIcon,
} from "lucide-react";
import {
  useGetMyPointBalance,
  useGetMyProfile,
} from "@/api/generated/member/member";
import { formatPoint } from "@/lib/format";
import { setAuthenticated, useIsAuthenticated, useNickname } from "@/lib/auth";
import { logout } from "@/api/generated/authentication/authentication";
import { Button } from "@/components/ui/button";
import { Logo } from "@/components/logo";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

type Role = "buyer" | "seller";

interface NavItem {
  label: string;
  to: string;
}

const NAV: Record<Role, NavItem[]> = {
  buyer: [
    { label: "홈", to: "/home" },
    { label: "경매", to: "/auctions" },
    { label: "관심", to: "/watchlist" },
    { label: "입찰 / 낙찰 내역", to: "/bids" },
  ],
  seller: [
    { label: "PickUp 홈", to: "/seller" },
    { label: "상품", to: "/seller/products" },
    { label: "판매 내역", to: "/seller/sales" },
  ],
};

/** 상단 글로벌 내비게이션 (DESIGN.md §4.1) */
export function Gnb({ role }: { role: Role }) {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const storedNickname = useNickname();
  const profileQuery = useGetMyProfile({
    query: { enabled: isAuthenticated },
  });
  const pointBalanceQuery = useGetMyPointBalance({
    query: { enabled: isAuthenticated },
  });
  const nickname = profileQuery.data?.nickname ?? storedNickname ?? "회원";
  const items = NAV[role];

  const pointBalance = pointBalanceQuery.data?.pointBalance;
  const pointBalanceLabel = pointBalanceQuery.isLoading
    ? "조회 중"
    : pointBalanceQuery.isError
      ? "조회 실패"
      : pointBalance === undefined
        ? "정보 없음"
        : formatPoint(pointBalance);

  const handleLogout = () => {
    logout().catch(() => {
      // 쿠키 만료 실패해도 클라이언트 쪽 로그인 상태는 초기화한다.
    });
    setAuthenticated(false);
    navigate({ to: "/login" });
  };

  return (
    <header
      className="sticky top-0 z-40 border-b border-border bg-[color-mix(in_srgb,var(--color-bg)_88%,transparent)] backdrop-blur"
      style={{ height: "var(--gnb-height)" }}
    >
      <div className="mx-auto flex h-full max-w-[var(--container-max)] items-center justify-between gap-3 px-4 md:gap-8 md:px-8">
        <div className="flex items-center gap-3 md:gap-8">
          <Link
            to={role === "seller" ? "/seller" : "/home"}
            className="flex items-center gap-2 text-lg font-bold"
          >
            <Logo role={role} className="size-5" />
            PickUp
            {role === "seller" && (
              <span className="rounded-[var(--radius-pill)] bg-[var(--color-seller-weak)] px-2 py-0.5 text-xs font-medium text-[var(--color-seller)]">
                셀러
              </span>
            )}
          </Link>

          {isAuthenticated && (
            <nav className="hidden items-center gap-1 md:flex">
              {items.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  activeOptions={{ exact: item.to === "/seller" }}
                  className="relative rounded-[var(--radius-sm)] px-3 py-2 text-sm whitespace-nowrap text-[var(--color-text-sub)] transition-colors hover:text-foreground"
                  activeProps={{
                    // rounded-[var(--radius-sm)]에 걸린 inset box-shadow는 바닥 모서리 라운드를
                    // 따라 양 끝이 말려 올라가 보인다(] 를 90도 돌린 모양). 부모 라운드에
                    // 영향받지 않는 별도의 pill 인디케이터(after)로 대체.
                    className:
                      "text-foreground font-semibold after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:rounded-full after:bg-primary after:content-['']",
                  }}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          )}
        </div>

        <div className="hidden items-center md:flex">
          {isAuthenticated ? (
            <DropdownMenu>
              <DropdownMenuTrigger className="flex items-center gap-1.5 rounded-[var(--radius-sm)] px-2 py-2 text-sm font-medium outline-none hover:bg-[var(--color-surface-2)] sm:px-3">
                <UserIcon className="size-4" />
                <span className="hidden sm:inline">MY</span>
                <ChevronDown className="size-4 text-[var(--color-text-muted)]" />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuLabel>{nickname} 님</DropdownMenuLabel>
                <div className="flex items-center justify-between px-3 py-1.5">
                  <span className="text-xs text-[var(--color-text-muted)]">
                    보유 포인트
                  </span>
                  <span className="tabular text-sm font-semibold text-primary">
                    {pointBalanceLabel}
                  </span>
                </div>
                <DropdownMenuSeparator />
                {role === "buyer" ? (
                  <>
                    <DropdownMenuItem
                      onSelect={() => navigate({ to: "/bids" })}
                    >
                      <Gavel /> 입찰 / 낙찰 내역
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onSelect={() => navigate({ to: "/watchlist" })}
                    >
                      <Heart /> 관심 목록
                    </DropdownMenuItem>
                  </>
                ) : (
                  <>
                    <DropdownMenuItem
                      onSelect={() => navigate({ to: "/seller/products" })}
                    >
                      <Package /> 상품 목록
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onSelect={() => navigate({ to: "/seller/sales" })}
                    >
                      <Receipt /> 판매 내역
                    </DropdownMenuItem>
                  </>
                )}
                <DropdownMenuItem
                  onSelect={() =>
                    navigate({
                      to: role === "buyer" ? "/settings" : "/seller/settings",
                    })
                  }
                >
                  <Settings /> 계정 설정
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onSelect={() =>
                    navigate({ to: role === "buyer" ? "/seller" : "/home" })
                  }
                >
                  <Repeat />
                  {role === "buyer" ? "셀러 모드 전환" : "구매자 모드 전환"}
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={handleLogout}>
                  <LogOut /> 로그아웃
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <div className="flex items-center gap-2">
              <Button asChild variant="ghost" size="sm">
                <Link to="/login">로그인</Link>
              </Button>
              <Button asChild size="sm">
                <Link to="/register">회원가입</Link>
              </Button>
            </div>
          )}
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label="메뉴 열기"
            className="flex items-center justify-center rounded-[var(--radius-sm)] p-2 outline-none hover:bg-[var(--color-surface-2)] md:hidden"
          >
            <Menu className="size-5" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {isAuthenticated ? (
              <>
                <DropdownMenuLabel>{nickname} 님</DropdownMenuLabel>
                <div className="flex items-center justify-between px-3 py-1.5">
                  <span className="text-xs text-[var(--color-text-muted)]">
                    보유 포인트
                  </span>
                  <span className="tabular text-sm font-semibold text-primary">
                    {pointBalanceLabel}
                  </span>
                </div>
                <DropdownMenuSeparator />
                {items.map((item) => (
                  <DropdownMenuItem key={item.to} asChild>
                    <Link
                      to={item.to}
                      activeOptions={{ exact: item.to === "/seller" }}
                      activeProps={{
                        className: "font-semibold text-foreground",
                      }}
                    >
                      {item.label}
                    </Link>
                  </DropdownMenuItem>
                ))}
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onSelect={() =>
                    navigate({
                      to: role === "buyer" ? "/settings" : "/seller/settings",
                    })
                  }
                >
                  <Settings /> 계정 설정
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() =>
                    navigate({ to: role === "buyer" ? "/seller" : "/home" })
                  }
                >
                  <Repeat />
                  {role === "buyer" ? "셀러 모드 전환" : "구매자 모드 전환"}
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={handleLogout}>
                  <LogOut /> 로그아웃
                </DropdownMenuItem>
              </>
            ) : (
              <>
                <DropdownMenuItem asChild>
                  <Link to="/login">로그인</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link to="/register">회원가입</Link>
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
