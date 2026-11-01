import type { ReactNode } from 'react';
import { ConsoleShell } from '@/components/console-shell';

export default function ConsoleLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <ConsoleShell>{children}</ConsoleShell>;
}
