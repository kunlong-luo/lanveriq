/** Only invented device fixtures. Never replace generated OpenAPI types with this file. */
export type State = 'online' | 'stale' | 'offline' | 'revoked';
export type Device = {id:string; name:string; group:string; os:string; state:State; heartbeat:string; inventory:string; queue:number; cpu:string; ram:string; agent:string};
export const devices:readonly Device[] = [
  {id:'00000000-0000-7000-8000-000000000001',name:'演示设备 A',group:'研发组',os:'Windows 11',state:'online',heartbeat:'刚刚（样例）',inventory:'10 分钟前（样例）',queue:0,cpu:'Intel Core i7',ram:'32 GB',agent:'0.1.0'},
  {id:'00000000-0000-7000-8000-000000000002',name:'演示设备 B',group:'研发组',os:'Pop!_OS 24.04',state:'online',heartbeat:'30 秒前（样例）',inventory:'2 小时前（样例）',queue:4,cpu:'AMD Ryzen 7',ram:'16 GB',agent:'0.1.0'},
  {id:'00000000-0000-7000-8000-000000000003',name:'演示设备 C',group:'运维组',os:'Windows 11',state:'stale',heartbeat:'3 分钟前（样例）',inventory:'昨天（样例）',queue:12,cpu:'Intel Core i5',ram:'16 GB',agent:'0.1.0'},
  {id:'00000000-0000-7000-8000-000000000004',name:'演示设备 D',group:'运维组',os:'Debian 13',state:'offline',heartbeat:'超过 5 分钟（样例）',inventory:'未知（样例）',queue:0,cpu:'未知',ram:'未知',agent:'0.1.0'},
  {id:'00000000-0000-7000-8000-000000000005',name:'演示设备 E',group:'测试组',os:'Windows 11',state:'revoked',heartbeat:'无（样例）',inventory:'无（样例）',queue:0,cpu:'未知',ram:'未知',agent:'0.1.0'}
];
export const stateLabels:Record<State,string>={online:'在线（样例）',stale:'延迟（样例）',offline:'离线（样例）',revoked:'已撤销（样例）'};
export function stateCounts(rows:readonly Device[]) {
  return rows.reduce((count,row)=>{count[row.state]++;return count;},{online:0,stale:0,offline:0,revoked:0});
}
export function filterDevices(rows:readonly Device[],search:string,group:string):Device[] {
  const term=search.trim().toLocaleLowerCase('zh-CN');
  return rows.filter(row=>(group==='全部分组'||row.group===group)&&(!term||[row.name,row.group,row.os].some(value=>value.toLocaleLowerCase('zh-CN').includes(term))));
}
