import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-[var(--radius-pill)] px-2.5 py-0.5 text-xs font-medium whitespace-nowrap",
  {
    variants: {
      variant: {
        neutral: "bg-[var(--color-surface-2)] text-[var(--color-text-sub)]",
        outline: "border border-border text-[var(--color-text-sub)]",
        live: "bg-[color-mix(in_srgb,var(--color-live)_18%,transparent)] text-[var(--color-live)]",
        warning:
          "bg-[color-mix(in_srgb,var(--color-warning)_18%,transparent)] text-[var(--color-warning)]",
        success:
          "bg-[color-mix(in_srgb,var(--color-success)_18%,transparent)] text-[var(--color-success)]",
        danger:
          "bg-[color-mix(in_srgb,var(--color-danger)_18%,transparent)] text-[var(--color-danger)]",
        muted: "bg-[var(--color-surface-2)] text-[var(--color-text-muted)]",
      },
    },
    defaultVariants: { variant: "neutral" },
  },
);

function Badge({
  className,
  variant,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return (
    <span className={cn(badgeVariants({ variant }), className)} {...props} />
  );
}

export { Badge, badgeVariants };
