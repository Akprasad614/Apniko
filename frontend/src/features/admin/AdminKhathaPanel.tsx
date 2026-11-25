import { useEffect, useState } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import '../customer/khatha.css'
import '../shopkeeper/khatha-workspace.css'
import './khatha-admin.css'

interface Overview{totalAccounts:number;activeAccounts:number;pausedAccounts:number;pendingRepayments:number;overdueAccounts:number;totalOutstanding:number}
interface Account{id:number;shopName:string;customerName:string;creditLimit:number;outstandingBalance:number;availableCredit:number;status:string;dueDate:string|null;overdue:boolean;repaymentDays:number}
interface Ledger{id:number;entryType:string;amount:number;outstandingAfter:number;description:string;actorName:string|null;actorRole:string|null;createdAt:string}
interface Repayment{id:number;accountId:number;amount:number;channel:string;status:string;reference:string|null;decisionReason:string|null;createdAt:string}
interface AuditEvent{id:number;accountId:number;action:string;reason:string;actorUserId:number;actorName:string;actorEmail:string;actorRole:string;createdAt:string}
const money=(v:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(v??0)
const date=(v:string|null)=>v?new Intl.DateTimeFormat('en-IN',{dateStyle:'medium',timeStyle:'short'}).format(new Date(v)):'—'
function message(error:unknown){return error instanceof ApiError?error.message:error instanceof Error?error.message:'Could not load Khatha reporting data.'}

export function AdminKhathaPanel({token}:{token:string}){
 const [overview,setOverview]=useState<Overview|null>(null)
 const [accounts,setAccounts]=useState<Account[]>([])
 const [selectedId,setSelectedId]=useState<number|null>(null)
 const [ledger,setLedger]=useState<Ledger[]>([])
 const [repayments,setRepayments]=useState<Repayment[]>([])
 const [auditEvents,setAuditEvents]=useState<AuditEvent[]>([])
 const [loading,setLoading]=useState(true)
 const [error,setError]=useState('')
 useEffect(()=>{
  let active=true
  Promise.all([
   apiRequest<Overview>('/api/admin/khatha/overview',{},token),
   apiRequest<Account[]>('/api/admin/khatha/accounts',{},token),
   apiRequest<Repayment[]>('/api/admin/khatha/repayments',{},token),
   apiRequest<AuditEvent[]>('/api/admin/khatha/audit-logs',{},token),
  ]).then(([summary,all,requests,events])=>{
   if(active){setOverview(summary);setAccounts(all);setRepayments(requests);setAuditEvents(events);setSelectedId(current=>current&&all.some(a=>a.id===current)?current:(all[0]?.id??null))}
  }).catch(e=>{if(active)setError(message(e))}).finally(()=>{if(active)setLoading(false)})
  return()=>{active=false}
 },[token])
 useEffect(()=>{
  if(selectedId===null){setLedger([]);return}
  let active=true
  apiRequest<Ledger[]>(`/api/admin/khatha/${selectedId}/ledger`,{},token)
   .then(rows=>{if(active)setLedger(rows)}).catch(e=>{if(active)setError(message(e))})
  return()=>{active=false}
 },[selectedId,token])
 const selected=accounts.find(a=>a.id===selectedId)??null
 const accountRepayments=selected?repayments.filter(r=>r.accountId===selected.id):[]
 return <section className="khatha-page khatha-admin">
  <header className="khatha-heading"><p className="khatha-eyebrow">PLATFORM FINANCIAL OVERSIGHT</p><h1>Khatha <em>monitor.</em></h1><p>Read-only visibility into shop credit, outstanding balances and repayment activity.</p></header>
  {error&&<div className="khatha-alert khatha-alert--error" role="alert">{error}</div>}
  {loading?<div className="khatha-empty" role="status">Loading Khatha overview…</div>:<>
   <div className="khatha-workspace-stats">
    <article><small>Accounts</small><strong>{overview?.totalAccounts??0}</strong></article>
    <article><small>Active</small><strong>{overview?.activeAccounts??0}</strong></article>
    <article><small>Overdue</small><strong>{overview?.overdueAccounts??0}</strong></article>
    <article><small>Total outstanding</small><strong>{money(overview?.totalOutstanding??0)}</strong></article>
   </div>
   <div className="khatha-admin-secondary-stats">
    <span>Paused accounts <b>{overview?.pausedAccounts??0}</b></span>
    <span>Pending repayment requests <b>{overview?.pendingRepayments??0}</b></span>
   </div>
   <div className="khatha-layout">
    <aside className="khatha-account-list"><p className="khatha-eyebrow">ALL ACCOUNTS</p>
     {accounts.length?accounts.map(a=><button type="button" key={a.id} className={'khatha-account-choice'+(selectedId===a.id?' is-selected':'')} onClick={()=>setSelectedId(a.id)}>
      <span><strong>{a.customerName}</strong><small>{a.shopName} · {a.status}</small></span><b>{money(a.outstandingBalance)}</b>
     </button>):<p className="khatha-help">No Khatha accounts exist yet.</p>}
    </aside>
    {selected&&<div className="khatha-detail">
     <div className="khatha-balance-card"><div className="khatha-balance-top"><span>{selected.customerName} · {selected.shopName}</span><span className={'khatha-status khatha-status--'+selected.status.toLowerCase()}>{selected.status}</span></div>
      <p>Outstanding balance</p><strong className="khatha-balance-value">{money(selected.outstandingBalance)}</strong>
      <div className="khatha-balance-metrics"><span><small>Credit limit</small><b>{money(selected.creditLimit)}</b></span><span><small>Available</small><b>{money(selected.availableCredit)}</b></span><span><small>Due date</small><b>{selected.dueDate??'—'}</b></span></div>
     </div>
     <section className="khatha-panel"><div className="khatha-panel-heading"><div><p className="khatha-eyebrow">APPEND-ONLY RECORD</p><h2>Ledger entries</h2></div><span>{ledger.length} records</span></div>
      {ledger.length?<div className="khatha-ledger">{ledger.map(e=><article key={e.id}><div><strong>{e.description}</strong><small>{date(e.createdAt)} · {e.entryType.replaceAll('_',' ')} · {e.actorName??'System'}</small></div><div><b>{money(e.amount)}</b><small>Balance {money(e.outstandingAfter)}</small></div></article>)}</div>:<p className="khatha-help">No ledger activity for this account.</p>}
     </section>
     <section className="khatha-panel"><div className="khatha-panel-heading"><div><p className="khatha-eyebrow">REPAYMENT EVIDENCE</p><h2>Repayment history</h2></div></div>
      {accountRepayments.length?<div className="khatha-ledger">{accountRepayments.map(r=><article key={r.id}><div><strong>{money(r.amount)} · {r.channel.replaceAll('_',' ')}</strong><small>{date(r.createdAt)}{r.reference?' · '+r.reference:''}</small>{r.decisionReason&&<small>{r.decisionReason}</small>}</div><span className={'khatha-status khatha-status--'+r.status.toLowerCase()}>{r.status}</span></article>)}</div>:<p className="khatha-help">No repayment requests for this account.</p>}
     </section>
    </div>}
   </div>
   <section className="khatha-panel khatha-admin-audit">
    <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">ACCOUNTABILITY</p><h2>Recent audit events</h2></div><span>{auditEvents.length} events</span></div>
    {auditEvents.length?<div className="khatha-ledger">{auditEvents.slice(0,30).map(event=><article key={event.id}>
     <div><strong>{event.action.replaceAll('_',' ')} · Account #{event.accountId}</strong><small>{date(event.createdAt)} · {event.actorName} ({event.actorRole})</small><small>{event.reason}</small></div>
    </article>)}</div>:<p className="khatha-help">No audit events have been recorded yet.</p>}
   </section>
   <p className="khatha-help khatha-admin-note">Administration is read-only in this workspace. Balance changes must be initiated by authorised shop/customer workflows and remain attributable in the audit trail.</p>
  </>}
 </section>
}
