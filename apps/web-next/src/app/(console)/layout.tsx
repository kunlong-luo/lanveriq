import type { ReactNode } from 'react';
import { ConsoleShell } from '@/components/console-shell';
import { PreviewQueryProvider } from '@/components/preview-query-provider';

export default function ConsoleLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <PreviewQueryProvider><ConsoleShell>{children}</ConsoleShell></PreviewQueryProvider>;
}
