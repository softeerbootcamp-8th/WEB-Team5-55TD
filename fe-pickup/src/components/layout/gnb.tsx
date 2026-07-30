import { Link, useNavigate } from "@tanstack/react-router";
import {
  ChevronDown,
  Gavel,
  Heart,
  LogOut,
  Package,
  Receipt,
  Repeat,
  Settings,
  User as UserIcon,
} from "lucide-react";
import { formatPoint } from "@/lib/format";
import { currentUser } from "@/lib/mock/data";
import { setAuthenticated, useIsAuthenticated, useNickname } from "@/lib/auth";
import { logout } from "@/api/generated/authentication/authentication";
import { Button } from "@/components/ui/button";
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
  const nickname = useNickname() ?? currentUser.nickname;
  const items = NAV[role];

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
      <div className="mx-auto flex h-full max-w-[var(--container-max)] items-center justify-between px-8">
        <div className="flex items-center gap-8">
          <Link
            to={role === "seller" ? "/seller" : "/home"}
            className="flex items-center gap-2 text-lg font-bold"
          >
            <span
              className="inline-block size-5 rounded-[6px] bg-primary"
              aria-hidden
            />
            PickUp
            {role === "seller" && (
              <span className="rounded-[var(--radius-pill)] bg-[var(--color-seller-weak)] px-2 py-0.5 text-xs font-medium text-[var(--color-seller)]">
                셀러
              </span>
            )}
          </Link>

          {isAuthenticated && (
            <nav className="flex items-center gap-1">
              {items.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  activeOptions={{ exact: item.to === "/seller" }}
                  className="rounded-[var(--radius-sm)] px-3 py-2 text-sm text-[var(--color-text-sub)] transition-colors hover:text-foreground"
                  activeProps={{
                    className:
                      "text-foreground font-semibold [box-shadow:inset_0_-2px_0_var(--primary)]",
                  }}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          )}
        </div>

        {isAuthenticated ? (
          <DropdownMenu>
            <DropdownMenuTrigger className="flex items-center gap-1.5 rounded-[var(--radius-sm)] px-3 py-2 text-sm font-medium outline-none hover:bg-[var(--color-surface-2)]">
              <UserIcon className="size-4" />
              MY
              <ChevronDown className="size-4 text-[var(--color-text-muted)]" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>{nickname} 님</DropdownMenuLabel>
              <div className="flex items-center justify-between px-3 py-1.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  보유 포인트
                </span>
                <span className="tabular text-sm font-semibold text-primary">
                  {formatPoint(currentUser.points)}
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
    </header>
  );
}
