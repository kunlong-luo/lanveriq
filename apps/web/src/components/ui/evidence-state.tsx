import { LockKeyhole, ShieldAlert, WifiOff } from 'lucide-react';

/**
 * NXT-27: one read-only vocabulary for absent device evidence. These are
 * distinctions, not fetch states or permission decisions. Never infer zero,
 * offline, healthy, or empty records from a disconnected Console preview.
 *
 * This component deliberately imports no API, session or client state.
 */
export type EvidenceStatus = 'unconnected' | 'restricted' | 'unverified';

const meanings = {
  unconnected: { label: '未连接', Icon: WifiOff },
  restricted: { label: '权限受限', Icon: LockKeyhole },
  unverified: { label: '尚未核验', Icon: ShieldAlert },
} as const;

export function EvidenceNotice({
  status, title, description, variant = 'detail',
}: Readonly<{
  status: EvidenceStatus;
  title: string;
  description: string;
  variant?: 'detail' | 'chart';
}>) {
  const { Icon } = meanings[status];
  return <div role="note" data-evidence-status={status}
    className={`lv-evidence-notice lv-evidence-notice--${variant} lv-evidence-notice--${status}`}>
    <span className="lv-evidence-icon" aria-hidden="true"><Icon size={21} /></span>
    <strong className="lv-evidence-title">{title}</strong>
    <p className="lv-evidence-description">{description}</p>
  </div>;
}

/** A text-only unknown value; never render 0 or an inferred device state. */
export function EvidenceValue({
  status = 'unconnected', context,
}: Readonly<{ status?: EvidenceStatus; context?: string }>) {
  const { label } = meanings[status];
  return <strong className="lv-evidence-value" data-evidence-status={status}
    aria-label={context ? `${context}：${label}` : undefined}>{label}</strong>;
}
