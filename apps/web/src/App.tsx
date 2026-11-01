import { useMemo, useState } from 'react';
import LoginPage from './LoginPage';
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation, useParams } from 'react-router';
import * as Tabs from '@radix-ui/react-tabs';
import { Activity, ArrowRight, Bell, Cable, CheckCircle2, ChevronRight, Clock3, Cpu, Database, HardDrive, LayoutDashboard, LockKeyhole, Menu, Monitor, Network, Plus, RefreshCcw, Search, Server, Settings2, ShieldCheck, WifiOff, type LucideIcon } from 'lucide-react';
import { devices, filterDevices, stateCounts, stateLabels, type Device, type State } from './demo';

const links:{to:string;title:string;icon:LucideIcon}[]=[
  {to:'/overview',title:'概览',icon:LayoutDashboard},
  {to:'/monitoring',title:'监控中心',icon:Activity},
  {to:'/devices',title:'终端资产',icon:Monitor},
  {to:'/enrollment',title:'设备注册',icon:Plus},
  {to:'/settings',title:'系统设置',icon:Settings2},
];

function Status({state}:{state:State}){
  return <span className={'status status-'+state}><span aria-hidden="true" className="status-dot"/>{stateLabels[state]}</span>;
}
function Heading({eyebrow,title,desc}:{eyebrow:string;title:string;desc:string}){
  return <div className="heading"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{desc}</p></div>;
}
function Shell(){
  const [open,setOpen]=useState(false);
  const location=useLocation();
  const active=links.find(item=>location.pathname.startsWith(item.to))?.title??'概览';
  return <div className="app">
    {open&&<button className="scrim" aria-label="关闭菜单" onClick={()=>setOpen(false)}/>}
    <aside className={'sidebar'+(open?' sidebar-open':'')}>
      <Link to="/overview" className="brand" onClick={()=>setOpen(false)}>
        <span className="brand-icon"><Activity size={24}/></span><span><strong>Lanveriq</strong><small>MANAGEMENT CONSOLE</small></span>
      </Link>
      <div className="nav-title">工作空间</div>
      <nav aria-label="主导航" className="nav-list">{links.map(({to,title,icon:Icon})=>
        <NavLink to={to} key={to} onClick={()=>setOpen(false)} className={({isActive})=>'nav-link'+(isActive?' nav-active':'')}>
          <Icon size={19}/><span>{title}</span><ChevronRight className="nav-arrow" size={15}/>
        </NavLink>)}</nav>
      <div className="sidebar-footer"><ShieldCheck size={20}/><div><strong>安全默认关闭</strong><small>尚未启用真实采集</small></div></div>
    </aside>
    <div className="app-main">
      <header className="topbar"><div className="top-left">
        <button className="menu-button" aria-label="打开菜单" onClick={()=>setOpen(true)}><Menu size={21}/></button>
        <span>工作空间</span><ChevronRight size={15}/><strong>{active}</strong>
      </div><div className="top-right"><span className="connection"><i/>未连接后端</span><button disabled aria-label="通知尚未接入"><Bell size={18}/></button><Link to="/login" className="avatar" title="预览管理员登录界面">DEMO</Link></div></header>
      <main className="main-content" id="main"><div className="demo-banner" role="status"><i/><strong>演示模式 · 全部为合成数据</strong><span>未连接后端、PostgreSQL、Redis 或真实终端；设备操作不可用</span></div><Outlet/></main>
      <footer>Lanveriq V0.1 · Synthetic UI Preview · 不可用于真实设备监控</footer>
    </div>
  </div>;
}
function Metric({label,value,icon:Icon,tone,description}:{label:string;value:number;icon:LucideIcon;tone:string;description:string}){
  return <div className="metric"><div className="metric-top"><span className={'metric-icon '+tone}><Icon size={20}/></span><small>演示数据</small></div><p>{label}</p><strong>{value}</strong><small className="metric-description">{description}</small></div>;
}
function Metrics(){
  const counts=stateCounts(devices);
  return <div className="metrics">
    <Metric label="演示设备总数" value={devices.length} icon={Monitor} tone="blue" description="全部为虚构终端"/>
    <Metric label="在线（样例）" value={counts.online} icon={CheckCircle2} tone="green" description="非真实在线状态"/>
    <Metric label="延迟及离线（样例）" value={counts.stale+counts.offline} icon={WifiOff} tone="orange" description="待真实心跳联调"/>
    <Metric label="已撤销（样例）" value={counts.revoked} icon={ShieldCheck} tone="red" description="撤销操作尚未开放"/>
  </div>;
}
function Table({rows}:{rows:readonly Device[]}){
  return <div className="table-scroll"><table>
    <thead><tr><th scope="col">终端设备</th><th scope="col">分组</th><th scope="col">连接状态</th><th scope="col">最后心跳</th><th scope="col">队列</th><th scope="col">详情</th></tr></thead>
    <tbody>{rows.map(row=><tr key={row.id}>
      <td><div className="device-title"><span className="device-icon"><Monitor size={19}/></span><div><strong>{row.name}</strong><small>{row.os} · Agent {row.agent}</small></div></div></td>
      <td>{row.group}</td><td><Status state={row.state}/></td><td>{row.heartbeat}</td><td>{row.queue} 条（样例）</td>
      <td><Link className="row-link" to={'/devices/'+row.id} aria-label={'查看'+row.name+'的合成详情'}><ArrowRight size={18}/></Link></td>
    </tr>)}</tbody></table>{rows.length===0&&<p className="empty">未找到匹配的演示设备，请调整筛选条件。</p>}</div>;
}
function Overview(){
  const counts=stateCounts(devices);
  return <section className="page"><div className="title-line"><Heading eyebrow="OVERVIEW / V0.1" title="设备运行概览" desc="集中查看终端连接状态与待处理事项。下方所有数字均来自本地示例。"/>
    <Link className="primary-link" to="/monitoring">查看监控中心 <ArrowRight size={16}/></Link></div>
    <Metrics/>
    <div className="columns">
      <section className="panel"><div className="panel-header"><div><h2>设备连接分布</h2><p>用于预览颜色和可访问的状态标签</p></div><span className="pill">合成示例</span></div>
        <div className="distribution"><div className="bars">{(['online','stale','offline','revoked'] as const).map(state=><span title={stateLabels[state]+': '+counts[state]} key={state} className={'bar-'+state} style={{flex:counts[state]||0.001}}/>)}</div>
        <div className="legend">{(['online','stale','offline','revoked'] as const).map(state=><div key={state}><i className={'bar-'+state}/><span>{stateLabels[state]}</span><strong>{counts[state]}</strong></div>)}</div></div>
        <div className="panel-note"><Clock3 size={19}/><span>心跳正常不代表库存或系统指标已更新。正式版分别显示数据新鲜度。</span></div>
      </section>
      <section className="panel"><div className="panel-header"><div><h2>平台连接状态</h2><p>当前页面不尝试连接任何服务</p></div></div>
        <Service icon={Server} name="Spring Boot API" text="业务接口默认关闭"/>
        <Service icon={Database} name="PostgreSQL / Redis" text="只能由后端访问"/>
        <Service icon={Network} name="WSS 设备状态推送" text="未完成真实浏览器与 TLS 验收"/>
        <Link to="/settings" className="panel-link">查看技术与安全验收状态 <ArrowRight size={15}/></Link>
      </section>
    </div>
    <section className="panel"><div className="panel-header"><div><h2>演示设备</h2><p>所有设备名称、心跳和队列都是虚构的。</p></div><Link to="/devices" className="text-link">查看全部 <ArrowRight size={15}/></Link></div><Table rows={devices.slice(0,4)}/></section>
  </section>;
}
function Service({icon:Icon,name,text}:{icon:LucideIcon;name:string;text:string}){
  return <div className="service"><span className="service-icon"><Icon size={19}/></span><div><strong>{name}</strong><small>{text}</small></div><span className="pill">未连接</span></div>;
}
function Monitoring(){
  const summary=stateCounts(devices);
  return <section className="page"><Heading eyebrow="MONITORING CENTER / V0.1" title="监控中心" desc="关注设备连接、待发送队列、状态缺口与来源能力，而不是员工屏幕画面。"/>
    <div className="metrics">
      <Metric label="在线（样例）" value={summary.online} icon={CheckCircle2} tone="green" description="虚构心跳"/>
      <Metric label="延迟（样例）" value={summary.stale} icon={Clock3} tone="orange" description="样例阈值 90–300 秒"/>
      <Metric label="离线（样例）" value={summary.offline} icon={WifiOff} tone="red" description="样例阈值超过 300 秒"/>
      <Metric label="待发送记录（样例）" value={devices.reduce((sum,item)=>sum+item.queue,0)} icon={Cable} tone="blue" description="非真实上传队列"/>
    </div>
    <section className="panel"><div className="panel-header"><div><h2>终端健康明细</h2><p>展示虚构的分组、Agent 版本和队列状态</p></div><span className="pill">非实时</span></div><Table rows={devices}/></section>
    <div className="info"><Clock3 size={21}/><div><strong>断线不等于没有数据</strong><p>正式版会区分离线、尚未采集、系统不支持、权限拒绝和传输缺口，不会把空值当作“无活动”。</p></div></div>
  </section>;
}
function Devices(){
  const [search,setSearch]=useState('');
  const [group,setGroup]=useState('全部分组');
  const visible=useMemo(()=>filterDevices(devices,search,group),[search,group]);
  return <section className="page"><div className="title-line"><Heading eyebrow="DEVICES / V0.1" title="终端资产" desc="按名称、操作系统或分组检索演示资产，进入只读详情。"/>
    <button disabled className="secondary-button" title="设备注册尚未通过认证和安全验收"><Plus size={16}/> 添加设备</button></div>
    <section className="panel"><div className="filters"><div><strong>设备列表</strong><small>{visible.length} 个结果（样例）</small></div>
      <div className="filter-controls"><label className="search"><Search size={17}/><span className="sr-only">搜索设备</span><input value={search} onChange={event=>setSearch(event.target.value)} placeholder="搜索设备或系统"/></label>
      <label><span className="sr-only">设备分组</span><select value={group} onChange={event=>setGroup(event.target.value)}><option>全部分组</option><option>研发组</option><option>运维组</option><option>测试组</option></select></label></div></div>
      <Table rows={visible}/><div className="table-foot">本地合成数据筛选；真实版由后端按当前登录账号权限过滤。</div>
    </section>
  </section>;
}
function Detail(){
  const {id}=useParams();
  const device=devices.find(item=>item.id===id);
  if(!device)return <section className="page"><Heading eyebrow="DEVICE DETAIL" title="演示设备不存在" desc="该设备编号不属于本地合成数据集。"/><Link to="/devices" className="text-link">返回列表 <ArrowRight size={15}/></Link></section>;
  return <section className="page"><Link to="/devices" className="back-link">终端资产 <ChevronRight size={15}/> {device.name}</Link>
    <div className="title-line"><Heading eyebrow="DEVICE DETAIL / SYNTHETIC" title={device.name} desc={device.os+' · '+device.group+' · 只读演示'}/><Status state={device.state}/></div>
    <div className="detail-summary"><div><Clock3 size={19}/><small>最后心跳（样例）</small><strong>{device.heartbeat}</strong></div><div><RefreshCcw size={19}/><small>资产快照（样例）</small><strong>{device.inventory}</strong></div><div><Cable size={19}/><small>队列（样例）</small><strong>{device.queue} 条</strong></div></div>
    <section className="panel"><Tabs.Root defaultValue="hardware"><Tabs.List aria-label="设备详情分类" className="tab-list"><Tabs.Trigger value="hardware">硬件</Tabs.Trigger><Tabs.Trigger value="network">网络</Tabs.Trigger><Tabs.Trigger value="health">健康</Tabs.Trigger></Tabs.List>
      <Tabs.Content className="tab-content" value="hardware"><div className="detail-grid"><DetailField icon={Cpu} label="CPU（样例）" value={device.cpu}/><DetailField icon={HardDrive} label="内存（样例）" value={device.ram}/><DetailField icon={Monitor} label="操作系统（样例）" value={device.os}/><DetailField icon={Server} label="Agent 版本（样例）" value={device.agent}/></div></Tabs.Content>
      <Tabs.Content className="tab-content" value="network"><div className="info"><LockKeyhole size={21}/><div><strong>敏感网络信息暂不展示</strong><p>正式版的 IP、MAC、网卡信息必须由服务器验证角色、分组及 internal_operational 授权。</p></div></div></Tabs.Content>
      <Tabs.Content className="tab-content" value="health"><div className="detail-grid"><DetailField icon={Activity} label="连接状态" value={stateLabels[device.state]}/><DetailField icon={Clock3} label="数据覆盖" value="无真实观测"/></div></Tabs.Content>
    </Tabs.Root></section>
    <section className="action-panel"><div><strong>设备操作（暂不可用）</strong><p>操作必须经过登录、设备授权和审计。</p></div><button disabled className="secondary-button"><RefreshCcw size={16}/> 申请刷新资产</button><button disabled className="danger-button">撤销证书</button></section>
  </section>;
}
function DetailField({icon:Icon,label,value}:{icon:LucideIcon;label:string;value:string}){
  return <div className="detail-field"><Icon size={21}/><div><small>{label}</small><strong>{value}</strong></div></div>;
}
function Enrollment(){
  return <section className="page"><Heading eyebrow="ENROLLMENT / V0.1" title="设备注册" desc="通过一次性凭据、CSR 核验、管理员审批与可信客户端证书接入终端。"/>
    <div className="columns"><section className="panel intro"><span className="intro-icon"><ShieldCheck size={29}/></span><h2>可信设备接入</h2><p>此页仅展示规划流程。当前尚未开放真实设备注册接口，所有功能必须先通过证书、TLS 与权限验收。</p><button className="primary-button" disabled>发起注册（未启用）</button></section>
    <section className="panel"><div className="panel-header"><div><h2>接入步骤</h2><p>设计说明，不执行任何设备操作</p></div></div>{['生成一次性注册凭据','终端生成密钥与 CSR','管理员审核并登记证书','mTLS 认证和设备绑定'].map((item,index)=><div className="step" key={item}><span>{index+1}</span>{item}</div>)}</section></div>
  </section>;
}
function Settings(){
  return <section className="page"><Heading eyebrow="SYSTEM / V0.1" title="系统设置" desc="查看工程依赖与验收门槛。此页不会修改服务器、Redis 或 PostgreSQL 配置。"/>
    <div className="columns"><section className="panel"><div className="panel-header"><div><h2>后端依赖</h2><p>全部为架构说明，无连接检测</p></div></div><Service icon={Server} name="Spring Boot 4.1.2 / Java 25" text="业务 API 默认拒绝"/><Service icon={Database} name="PostgreSQL 18" text="权威设备、审计及事务数据"/><Service icon={Database} name="Redis 8 / Lettuce" text="非权威短期提示，默认关闭"/><Service icon={Monitor} name="Rust Agent" text="运行服务框架，尚未连接真实采集"/></section>
    <section className="panel"><div className="panel-header"><div><h2>待完成验收</h2><p>完成代码不等于获得部署许可</p></div></div>{['PostgreSQL 18 全量集成测试','Caddy TLS、浏览器 Session 和 CSRF','设备证书与 OS 服务生命周期','React 与真实 API 的授权联调'].map(item=><div className="check" key={item}><Clock3 size={17}/><span>{item}</span><strong>待完成</strong></div>)}<div className="warning">当前未开启真实终端监控、远程命令或屏幕录制。</div></section></div>
  </section>;
}
export default function App(){
  return <Routes><Route path="/login" element={<LoginPage/>}/><Route path="/" element={<Shell/>}><Route index element={<Navigate to="/overview" replace/>}/><Route path="overview" element={<Overview/>}/><Route path="monitoring" element={<Monitoring/>}/><Route path="devices" element={<Devices/>}/><Route path="devices/:id" element={<Detail/>}/><Route path="enrollment" element={<Enrollment/>}/><Route path="settings" element={<Settings/>}/><Route path="*" element={<Navigate to="/overview" replace/>}/></Route></Routes>;
}
