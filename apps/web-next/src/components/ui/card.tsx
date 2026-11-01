import type { ComponentProps } from 'react';

// Editable shadcn/ui-compatible Card; carried forward from apps/web.
function Card({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card"
    className={`bg-card text-card-foreground flex flex-col gap-6 rounded-xl border border-border py-6 shadow-sm ${className}`}
    {...props} />;
}
function CardHeader({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-header" className={`grid auto-rows-min items-start gap-1.5 px-6 ${className}`} {...props} />;
}
function CardTitle({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-title" className={`font-semibold tracking-tight ${className}`} {...props} />;
}
function CardDescription({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-description" className={`text-muted-foreground text-sm ${className}`} {...props} />;
}
function CardContent({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-content" className={`px-6 ${className}`} {...props} />;
}
export { Card, CardHeader, CardTitle, CardDescription, CardContent };
