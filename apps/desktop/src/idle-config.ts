// Mirrors the actual Rust AgentConfig v1: schema_version + runtime.cycle_seconds.
// This UI deliberately offers a small reviewed subset of Rust's 5..=300
// seconds. The output is an untrusted, offline preview; never an install token.
export const IDLE_INTERVAL_OPTIONS = [
  { seconds: 15, label: "15 秒" },
  { seconds: 30, label: "30 秒（推荐）" },
  { seconds: 60, label: "1 分钟" },
  { seconds: 120, label: "2 分钟" },
  { seconds: 300, label: "5 分钟" },
] as const;

export type IdleInterval = (typeof IDLE_INTERVAL_OPTIONS)[number]["seconds"];

export function buildOfflineIdleConfig(value: unknown): string {
  if (typeof value !== "number" || !Number.isInteger(value) ||
      !IDLE_INTERVAL_OPTIONS.some(option => option.seconds === value)) {
    throw new Error("UNAPPROVED_IDLE_INTERVAL");
  }
  return `schema_version = 1\n\n[runtime]\ncycle_seconds = ${value}\n`;
}
