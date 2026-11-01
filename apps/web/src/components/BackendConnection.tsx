import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, RefreshCcw, Server, ShieldCheck, WifiOff } from 'lucide-react';
import { probeBackendHealth } from '@/api/backendHealth';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

type Status = 'checking' | 'up' | 'unavailable';

function useBackendHealth() {
  return useQuery({
    queryKey: ['public-backend-health'],
    queryFn: ({ signal }) => probeBackendHealth(fetch, signal),
    retry: false,
    staleTime: Infinity,
    refetchInterval: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

function state(data: 'up' | 'unavailable' | undefined, isPending: boolean): Status {
  if (isPending) return 'checking';
  return data === 'up' ? 'up' : 'unavailable';
}

const descriptions: Record<Status, string> = {
  checking: '正在检查本地后端',
  up: 'Spring Boot 健康检查通过',
  unavailable: '未连接后端健康接口',
};

export function BackendConnection() {
  const { data, isPending, isFetching, refetch } = useBackendHealth();
  const current = state(data, isPending);
  return <div className={`connection-indicator connection-${current}`} role="status"
    aria-label={descriptions[current]}>
    <span className="connection-dot" aria-hidden="true" />
    <span>{descriptions[current]}</span>
    <button type="button" aria-label="重新检查后端健康状态"
      title="手动重试一次健康检查；不会查询设备数据"
      disabled={isFetching} onClick={() => void refetch()}>
      <RefreshCcw size={14} aria-hidden="true" />
    </button>
  </div>;
}

export function BackendHealthCard() {
  const { data, isPending, isFetching, refetch } = useBackendHealth();
  const current = state(data, isPending);
  const healthy = current === 'up';
  return <Card className="health-card">
    <CardHeader className="health-card-heading">
      <div className="health-card-icon"><Server size={20} aria-hidden="true" /></div>
      <div>
        <CardTitle>Spring Boot 连接检测</CardTitle>
        <CardDescription>只读 GET /actuator/health · 本地开发联调</CardDescription>
      </div>
      <span className={`health-tag health-tag-${current}`} role="status">
        {healthy ? <CheckCircle2 size={15} aria-hidden="true" /> : <WifiOff size={15} aria-hidden="true" />}
        {current === 'checking' ? '检查中' : healthy ? '后端健康' : '未连接后端'}
      </span>
    </CardHeader>
    <CardContent className="health-card-body">
      <div className="health-description">
        <strong>{descriptions[current]}</strong>
        <p>{healthy
          ? '仅确认 Spring Boot 健康端点返回 UP；不代表登录、数据库事务或设备业务接口已通过验收。'
          : '请保持 IDEA 中的 Java 后端运行，并检查实际端口是 8080 还是 18081。设备数据仍是演示数据。'}</p>
      </div>
      <button type="button" className="health-refresh" onClick={() => void refetch()} disabled={isFetching}>
        <RefreshCcw size={16} aria-hidden="true" /> 重新检查
      </button>
    </CardContent>
    <div className="health-security-note"><ShieldCheck size={16} aria-hidden="true" />
      未启用真实管理员登录、业务 API、终端采集或自动轮询
    </div>
  </Card>;
}
