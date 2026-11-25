import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import '../customer/khatha.css'
import './khatha-workspace.css'

interface Account {
  id:number; shopId:number; shopName:string; customerId:number; customerName:string
  creditLimit:number; outstandingBalance:number; availableCredit:number; creditBalance:number; status:string
  repaymentDays:number; dueDate:string|null; overdue:boolean
}
interface Customer {id:number;name:string;email:string}
interface Ledger {id:number;entryType:string;amount:number;outstandingAfter:number;description:string;createdAt:string;creditBalanceAfter:number|null;creditBalanceChange:number|null;outstandingChange:number|null}
interface Repayment {id:number;accountId:number;amount:number;channel:string;status:string;reference:string|null;note:string|null;decisionReason:string|null;createdAt:string}
const money=(v:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(v??0)
const date=(v:string|null)=>v?new Intl.DateTimeFormat('en-IN',{dateStyle:'medium',timeStyle:'short'}).format(new Date(v)):'—'
function message(error:unknown){return error instanceof ApiError?error.message:error instanceof Error?error.message:'Something went wrong. Try again.'}

export function ShopkeeperKhathaWorkspace({token,shopId}:{token:string;shopId:number}){
 const [accounts,setAccounts]=useState<Account[]>([])
 const [customers,setCustomers]=useState<Customer[]>([])
 const [selectedId,setSelectedId]=useState<number|null>(null)
 const [ledger,setLedger]=useState<Ledger[]>([])
 const [repayments,setRepayments]=useState<Repayment[]>([])
 const [loading,setLoading]=useState(true)
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const [notice,setNotice]=useState('')
 const [customerId,setCustomerId]=useState('')
 const [limit,setLimit]=useState('2000')
 const [days,setDays]=useState('15')
 const [editLimit,setEditLimit]=useState('')
 const [editDays,setEditDays]=useState('15')
 const [termsReason,setTermsReason]=useState('Updated credit arrangement agreed with customer')
 const [repaymentAmount,setRepaymentAmount]=useState('')
 const [adjustmentAmount,setAdjustmentAmount]=useState('')
 const [adjustmentType,setAdjustmentType]=useState<'CREDIT'|'DEBIT'>('CREDIT')
 const [adjustmentReason,setAdjustmentReason]=useState('Correction agreed with customer')

 const load=useCallback(async()=>{
  setLoading(true);setError('')
  const results=await Promise.allSettled([
   apiRequest<Account[]>(`/api/shopkeeper/khatha?shopId=${shopId}`,{},token),
   apiRequest<Customer[]>(`/api/shopkeeper/khatha/shops/${shopId}/customers`,{},token),
  ])
  if(results[0].status==='fulfilled'){
   const loadedAccounts=results[0].value
   setAccounts(loadedAccounts)
   setSelectedId(current=>current&&loadedAccounts.some(a=>a.id===current)?current:(loadedAccounts[0]?.id??null))
  }else setError(message(results[0].reason))
  if(results[1].status==='fulfilled')setCustomers(results[1].value)
  else setError(message(results[1].reason))
  setLoading(false)
 },[token,shopId])
 useEffect(()=>{void load()},[load])
 useEffect(()=>{
  if(selectedId===null){setLedger([]);setRepayments([]);return}
  let active=true
  Promise.all([
   apiRequest<Ledger[]>(`/api/shopkeeper/khatha/${selectedId}/ledger`,{},token),
   apiRequest<Repayment[]>(`/api/shopkeeper/khatha/${selectedId}/repayments`,{},token),
  ]).then(([l,r])=>{if(active){setLedger(l);setRepayments(r)}})
   .catch(e=>{if(active)setError(message(e))})
  return()=>{active=false}
 },[selectedId,token])
 const selected=accounts.find(a=>a.id===selectedId)??null
 function updateAccount(updated:Account){setAccounts(current=>current.map(a=>a.id===updated.id?updated:a))}
 async function offerCredit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();setBusy(true);setError('');setNotice('')
  try{
   const result=await apiRequest<Account>('/api/shopkeeper/khatha/offers',{method:'POST',body:JSON.stringify({
    shopId,customerId:Number(customerId),creditLimit:Number(limit),repaymentDays:Number(days),
   })},token)
   setAccounts(current=>[result,...current]);setSelectedId(result.id);setCustomerId('')
   setNotice('Credit offer created. The customer must accept the terms before using it.')
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function setStatus(status:'PAUSED'|'ACTIVE'|'CLOSED'){
  if(!selected)return
  setBusy(true);setError('');setNotice('')
  try{
   const result=await apiRequest<Account>(`/api/shopkeeper/khatha/${selected.id}/status`,{method:'PATCH',body:JSON.stringify({status})},token)
   updateAccount(result);setNotice('Khatha account updated.')
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function saveTerms(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(!selected)return
  setBusy(true);setError('');setNotice('')
  try{
   const result=await apiRequest<Account>(`/api/shopkeeper/khatha/${selected.id}/terms`,{method:'PATCH',body:JSON.stringify({
    creditLimit:Number(editLimit),repaymentDays:Number(editDays),reason:termsReason,
   })},token)
   updateAccount(result);setNotice('New terms saved. The customer must accept them before using Khatha again.')
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function decideRepayment(repaymentId:number,decision:'confirm'|'reject'){
  if(!selected)return
  setBusy(true);setError('');setNotice('')
  try{
   const result=await apiRequest<Repayment>(`/api/shopkeeper/khatha/${selected.id}/repayments/${repaymentId}/${decision}`,{
    method:'POST',body:JSON.stringify({reason:decision==='reject'?'Shopkeeper could not verify receipt':null}),
   },token)
   setRepayments(current=>current.map(r=>r.id===result.id?result:r))
   if(decision==='confirm'){setNotice('Repayment verified and ledger balance updated.');await load()}
   else setNotice('Repayment request rejected. The outstanding balance is unchanged.')
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function recordCash(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(!selected)return
  setBusy(true);setError('');setNotice('')
  try{
   const result=await apiRequest<Repayment>(`/api/shopkeeper/khatha/${selected.id}/repayments`,{
    method:'POST',body:JSON.stringify({amount:Number(repaymentAmount),channel:'CASH_AT_SHOP',idempotencyKey:crypto.randomUUID(),note:'Cash received and recorded by shopkeeper'}),
   },token)
   setRepayments(current=>[result,...current]);setRepaymentAmount('')
   setNotice('Cash repayment recorded and deducted from the outstanding balance.');await load()
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function adjustBalance(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(!selected)return
  setBusy(true);setError('');setNotice('')
  try{
   await apiRequest<Ledger>(`/api/shopkeeper/khatha/${selected.id}/adjustments`,{
    method:'POST',body:JSON.stringify({amount:Number(adjustmentAmount),type:adjustmentType,reason:adjustmentReason}),
   },token)
   setAdjustmentAmount('');setNotice('Audited balance adjustment recorded.');await load()
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }

 return <section className="khatha-page khatha-workspace">
  <header className="khatha-heading"><p className="khatha-eyebrow">CUSTOMER CREDIT & LEDGER</p><h1>Shop <em>Khatha.</em></h1><p>Offer credit, verify repayments and keep every balance accountable.</p></header>
  {error&&<div className="khatha-alert khatha-alert--error" role="alert">{error}</div>}
  {notice&&<div className="khatha-alert" role="status">{notice}</div>}
  <div className="khatha-workspace-stats">
   <article><small>Accounts</small><strong>{accounts.length}</strong></article>
   <article><small>Total outstanding</small><strong>{money(accounts.reduce((n,a)=>n+a.outstandingBalance,0))}</strong></article>
   <article><small>Overdue</small><strong>{accounts.filter(a=>a.overdue||a.status==='OVERDUE').length}</strong></article>
   <article><small>Awaiting acceptance</small><strong>{accounts.filter(a=>a.status==='OFFERED').length}</strong></article>
  </div>
  <section className="khatha-panel">
   <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">NEW CREDIT OFFER</p><h2>Offer Khatha to a customer</h2></div></div>
   {customers.length===0?<p className="khatha-help">Customers who have placed an order with this shop will appear here.</p>:
    <form className="khatha-form khatha-form--offer" onSubmit={offerCredit}>
     <label>Customer<select value={customerId} onChange={e=>setCustomerId(e.target.value)} required><option value="">Choose customer</option>{customers.filter(c=>!accounts.some(a=>a.customerId===c.id)).map(c=><option value={c.id} key={c.id}>{c.name} · {c.email}</option>)}</select></label>
     <label>Credit limit (₹)<input type="number" min="1" step="0.01" value={limit} onChange={e=>setLimit(e.target.value)} required /></label>
     <label>Repayment period (days)<input type="number" min="1" max="90" value={days} onChange={e=>setDays(e.target.value)} required /></label>
     <p className="khatha-help">The customer must review and accept the offered limit and due terms. Interest and late fees are not enabled.</p>
     <button className="khatha-primary" type="submit" disabled={busy||!customerId}>Create credit offer</button>
    </form>}
  </section>
  {loading?<div className="khatha-empty" role="status">Loading shop Khatha accounts…</div>:
   <div className="khatha-layout">
    <aside className="khatha-account-list" aria-label="Shop customer accounts">
     <p className="khatha-eyebrow">CUSTOMER ACCOUNTS</p>
     {accounts.length?accounts.map(a=><button type="button" key={a.id} className={'khatha-account-choice'+(selectedId===a.id?' is-selected':'')} onClick={()=>setSelectedId(a.id)}>
      <span><strong>{a.customerName}</strong><small>{a.status.replaceAll('_',' ')}</small></span><b>{money(a.outstandingBalance)}</b>
     </button>):<p className="khatha-help">No Khatha accounts yet.</p>}
    </aside>
    {selected&&<div className="khatha-detail">
     <div className="khatha-balance-card"><div className="khatha-balance-top"><span>{selected.customerName}</span><span className={'khatha-status khatha-status--'+selected.status.toLowerCase()}>{selected.status}</span></div>
      <p>Outstanding balance</p><strong className="khatha-balance-value">{money(selected.outstandingBalance)}</strong>
      <div className="khatha-balance-metrics"><span><small>Credit limit</small><b>{money(selected.creditLimit)}</b></span><span><small>Available</small><b>{money(selected.availableCredit)}</b></span><span><small>Customer credit</small><b>{money(selected.creditBalance)}</b></span><span><small>Due date</small><b>{date(selected.dueDate)}</b></span></div>
     </div>
     <section className="khatha-panel">
      <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">ACCOUNT CONTROLS</p><h2>Manage terms and status</h2></div></div>
      <div className="khatha-actions">
       {selected.status==='ACTIVE'||selected.status==='OVERDUE'?<button type="button" className="khatha-secondary" disabled={busy} onClick={()=>void setStatus('PAUSED')}>Pause account</button>:selected.status==='PAUSED'?<button type="button" className="khatha-secondary" disabled={busy} onClick={()=>void setStatus('ACTIVE')}>Reactivate account</button>:null}
       {selected.outstandingBalance===0&&selected.status!=='CLOSED'&&<button type="button" className="khatha-secondary" disabled={busy} onClick={()=>void setStatus('CLOSED')}>Close account</button>}
       {selected.status!=='CLOSED'&&<button type="button" className="khatha-secondary" onClick={()=>{setEditLimit(String(selected.creditLimit));setEditDays(String(selected.repaymentDays))}}>Edit credit terms</button>}
      </div>
      {editLimit!==''&&<form className="khatha-form" onSubmit={saveTerms}>
       <label>New credit limit (₹)<input type="number" min={selected.outstandingBalance} step="0.01" value={editLimit} onChange={e=>setEditLimit(e.target.value)} required /></label>
       <label>Repayment period (days)<input type="number" min="1" max="90" value={editDays} onChange={e=>setEditDays(e.target.value)} required /></label>
       <label>Reason for change<input value={termsReason} onChange={e=>setTermsReason(e.target.value)} minLength={8} maxLength={500} required /></label>
       <button className="khatha-primary" type="submit" disabled={busy}>Save new terms</button>
      </form>}
     </section>
     <section className="khatha-panel">
      <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">REPAYMENT REVIEW</p><h2>Customer requests</h2></div></div>
      {repayments.length? <div className="khatha-ledger">{repayments.map(r=><article key={r.id}>
       <div><strong>{money(r.amount)} · {r.channel.replaceAll('_',' ')}</strong><small>{date(r.createdAt)}{r.reference?' · '+r.reference:''}</small><small>{r.note??''}</small></div>
       <div><span className={'khatha-status khatha-status--'+r.status.toLowerCase()}>{r.status}</span>{r.status==='PENDING'&&<div className="khatha-actions"><button type="button" className="khatha-confirm" disabled={busy} onClick={()=>void decideRepayment(r.id,'confirm')}>Confirm receipt</button><button type="button" className="khatha-secondary" disabled={busy} onClick={()=>void decideRepayment(r.id,'reject')}>Reject</button></div>}</div>
      </article>)}</div>:<p className="khatha-help">No repayment requests for this account.</p>}
      <form className="khatha-form khatha-direct-repayment" onSubmit={recordCash}>
       <label>Cash received (₹)<input type="number" min="0.01" max={selected.outstandingBalance} step="0.01" value={repaymentAmount} onChange={e=>setRepaymentAmount(e.target.value)} required disabled={selected.outstandingBalance<=0}/></label>
       <button className="khatha-primary" type="submit" disabled={busy||selected.outstandingBalance<=0}>Record cash received</button>
      </form>
     </section>
     <section className="khatha-panel">
      <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">AUDITABLE CORRECTION</p><h2>Balance adjustment</h2></div></div>
      <form className="khatha-form" onSubmit={adjustBalance}>
       <label>Adjustment type<select value={adjustmentType} onChange={e=>setAdjustmentType(e.target.value as typeof adjustmentType)}><option value="CREDIT">Credit · reduce amount owed</option><option value="DEBIT">Debit · increase amount owed</option></select></label>
       <label>Amount (₹)<input type="number" min="0.01" step="0.01" value={adjustmentAmount} onChange={e=>setAdjustmentAmount(e.target.value)} required /></label>
       <label>Reason<input value={adjustmentReason} onChange={e=>setAdjustmentReason(e.target.value)} minLength={8} maxLength={500} required /></label>
       <button className="khatha-primary" type="submit" disabled={busy}>Save audited adjustment</button>
      </form>
     </section>
     <section className="khatha-panel"><div className="khatha-panel-heading"><div><p className="khatha-eyebrow">IMMUTABLE HISTORY</p><h2>Ledger</h2></div></div>
      {ledger.length?<div className="khatha-ledger">{ledger.map(l=><article key={l.id}><div><strong>{l.description}</strong><small>{date(l.createdAt)} · {l.entryType.replaceAll('_',' ')}</small></div><div><b>{money(l.amount)}</b><small>Balance {money(l.outstandingAfter)} · Credit {money(l.creditBalanceAfter??0)}</small></div></article>)}</div>:<p className="khatha-help">No ledger entries.</p>}
     </section>
    </div>}
   </div>}
 </section>
}
