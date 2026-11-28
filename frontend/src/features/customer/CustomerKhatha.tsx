import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import { useAuth } from '../../auth/AuthProvider'
import './khatha.css'

interface Account {
  id:number; shopId:number; shopName:string; customerName:string
  creditLimit:number; outstandingBalance:number; availableCredit:number; creditBalance:number
  status:string; repaymentDays:number; dueDate:string|null; overdue:boolean
  termsVersion:number; acceptedTermsVersion:number
}
interface LedgerEntry {
  id:number; entryType:string; amount:number; outstandingAfter:number
  description:string; actorName:string|null; actorRole:string|null; dueDate:string|null; createdAt:string; creditBalanceAfter:number|null; creditBalanceChange:number|null; outstandingChange:number|null
}
interface Repayment {
  id:number; accountId:number; amount:number; channel:string; status:string
  reference:string|null; note:string|null; decisionReason:string|null; createdAt:string
}
const money=(value:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(value??0)
const date=(value:string|null)=>value?new Intl.DateTimeFormat('en-IN',{dateStyle:'medium'}).format(new Date(value)):'—'
function errorMessage(error:unknown){
  if(error instanceof ApiError) return error.message
  return error instanceof Error?error.message:'Could not load Khatha. Please try again.'
}

export function CustomerKhatha(){
  const {session}=useAuth()
  const token=session?.token??''
  const [accounts,setAccounts]=useState<Account[]>([])
  const [selectedId,setSelectedId]=useState<number|null>(null)
  const [ledger,setLedger]=useState<LedgerEntry[]>([])
  const [repayments,setRepayments]=useState<Repayment[]>([])
  const [loading,setLoading]=useState(true)
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const [amount,setAmount]=useState('')
  const [channel,setChannel]=useState<'CASH_AT_SHOP'|'UPI_TRANSFER'>('CASH_AT_SHOP')
  const [reference,setReference]=useState('')
  const [note,setNote]=useState('')

  const loadAccounts=useCallback(async()=>{
    setLoading(true); setError('')
    try{
      const result=await apiRequest<Account[]>('/api/customer/khatha',{},token)
      setAccounts(result)
      setSelectedId(current=>current && result.some(a=>a.id===current)?current:(result[0]?.id??null))
    }catch(e){setError(errorMessage(e))}
    finally{setLoading(false)}
  },[token])
  useEffect(()=>{void loadAccounts()},[loadAccounts])
  useEffect(()=>{
    if(selectedId===null){setLedger([]);setRepayments([]);return}
    let active=true
    Promise.all([
      apiRequest<LedgerEntry[]>(`/api/customer/khatha/${selectedId}/ledger`,{},token),
      apiRequest<Repayment[]>(`/api/customer/khatha/${selectedId}/repayments`,{},token),
    ]).then(([entries,requests])=>{
      if(active){setLedger(entries);setRepayments(requests)}
    }).catch(e=>{if(active)setError(errorMessage(e))})
    return()=>{active=false}
  },[selectedId,token])

  const selected=accounts.find(a=>a.id===selectedId)??null
  async function acceptOffer(){
    if(!selected)return
    setBusy(true);setError('');setNotice('')
    try{
      const updated=await apiRequest<Account>(`/api/customer/khatha/${selected.id}/accept`,{method:'POST'},token)
      setAccounts(current=>current.map(a=>a.id===updated.id?updated:a))
      setNotice('Khatha terms accepted. You can now use this shop’s available credit.')
    }catch(e){setError(errorMessage(e))}
    finally{setBusy(false)}
  }
  async function requestRepayment(event:FormEvent<HTMLFormElement>){
    event.preventDefault()
    if(!selected||!amount)return
    setBusy(true);setError('');setNotice('')
    try{
      const result=await apiRequest<Repayment>(`/api/customer/khatha/${selected.id}/repayments`,{
        method:'POST',
        body:JSON.stringify({amount:Number(amount),channel,reference:reference.trim()||null,note:note.trim()||null,idempotencyKey:crypto.randomUUID()}),
      },token)
      setRepayments(current=>[result,...current])
      setAmount('');setReference('');setNote('')
      setNotice('Repayment submitted for shopkeeper verification. Your balance will change only after the shop confirms receipt.')
    }catch(e){setError(errorMessage(e))}
    finally{setBusy(false)}
  }

  return <section className="khatha-page">
    <header className="khatha-heading">
      <p className="khatha-eyebrow">YOUR LOCAL SHOP CREDIT</p>
      <h1>Apniko <em>Khatha.</em></h1>
      <p>A clear record of the credit you use with each neighbourhood shop.</p>
    </header>
    {error&&<div className="khatha-alert khatha-alert--error" role="alert">{error}</div>}
    {notice&&<div className="khatha-alert" role="status">{notice}</div>}
    {loading?<div className="khatha-empty" role="status">Loading your Khatha accounts…</div>:accounts.length===0?
      <div className="khatha-empty"><strong>No Khatha accounts yet.</strong><p>A shopkeeper can offer you credit. The offer will appear here for you to review and accept.</p></div>:
      <>
        <div className="khatha-layout">
          <aside className="khatha-account-list" aria-label="Your shop accounts">
            <p className="khatha-eyebrow">YOUR SHOPS</p>
            {accounts.map(account=><button type="button" key={account.id}
              className={'khatha-account-choice'+(selectedId===account.id?' is-selected':'')}
              onClick={()=>{setSelectedId(account.id);setError('');setNotice('')}}>
              <span><strong>{account.shopName}</strong><small>{account.status==='OVERDUE'?'Payment overdue':account.status.replaceAll('_',' ')}</small></span>
              <b>{money(account.outstandingBalance)}</b>
            </button>)}
          </aside>
          {selected&&<div className="khatha-detail">
            <div className="khatha-balance-card">
              <div className="khatha-balance-top"><span>{selected.shopName}</span><span className={'khatha-status khatha-status--'+selected.status.toLowerCase()}>{selected.status.replaceAll('_',' ')}</span></div>
              <p>Outstanding balance</p><strong className="khatha-balance-value">{money(selected.outstandingBalance)}</strong>
              <div className="khatha-balance-metrics">
                <span><small>Credit limit</small><b>{money(selected.creditLimit)}</b></span>
                <span><small>Available credit</small><b>{money(selected.availableCredit)}</b></span><span><small>Shop credit balance</small><b>{money(selected.creditBalance)}</b></span>
                <span><small>Due date</small><b>{date(selected.dueDate)}</b></span>
              </div>
            </div>
            {selected.status==='OFFERED'&&<section className="khatha-panel khatha-offer">
              <div><p className="khatha-eyebrow">CREDIT OFFER</p><h2>Review your Khatha terms</h2></div>
              <p><strong>{selected.shopName}</strong> is offering a credit limit of <strong>{money(selected.creditLimit)}</strong> with a repayment period of <strong>{selected.repaymentDays} days</strong>.</p>
              <p>No interest or late fee is currently configured. You may use this credit only after accepting these terms.</p>
              <button className="khatha-primary" type="button" disabled={busy} onClick={()=>void acceptOffer()}>{busy?'Saving…':'Accept Khatha terms'}</button>
            </section>}
            <section className="khatha-panel">
              <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">REPAYMENT</p><h2>Record a repayment</h2></div></div>
              {selected.outstandingBalance>0?<form className="khatha-form" onSubmit={requestRepayment}>
                <label>Amount (₹)<input type="number" min="0.01" max={selected.outstandingBalance} step="0.01" value={amount} onChange={e=>setAmount(e.target.value)} required /></label>
                <label>How did you pay?<select value={channel} onChange={e=>setChannel(e.target.value as typeof channel)}><option value="CASH_AT_SHOP">Cash paid at shop</option><option value="UPI_TRANSFER">UPI transfer</option></select></label>
                {channel==='UPI_TRANSFER'&&<label>UPI transaction reference<input value={reference} onChange={e=>setReference(e.target.value)} maxLength={120} required /></label>}
                <label>Note (optional)<input value={note} onChange={e=>setNote(e.target.value)} maxLength={500} placeholder="Any details for the shopkeeper" /></label>
                <p className="khatha-help">This sends a repayment request to the shop. Your balance will not decrease until the shopkeeper confirms the payment.</p>
                <button type="submit" className="khatha-primary" disabled={busy}>{busy?'Submitting…':'Submit repayment for verification'}</button>
              </form>:<p className="khatha-help">You have no outstanding balance with this shop.</p>}
            </section>
            <section className="khatha-panel">
              <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">ACCOUNT HISTORY</p><h2>Transactions</h2></div><span>{ledger.length} entries</span></div>
              {ledger.length===0?<p className="khatha-help">Your ledger will appear here when you use Khatha.</p>:
                <div className="khatha-ledger">{ledger.map(entry=><article key={entry.id}>
                  <div><strong>{entry.description}</strong><small>{date(entry.createdAt)} · {entry.entryType.replaceAll('_',' ')}</small></div>
                  <div><b>{entry.entryType==='PURCHASE_DEBIT'||entry.entryType==='ADJUSTMENT_DEBIT'?'+':'−'}{money(entry.amount)}</b><small>Balance {money(entry.outstandingAfter)} · Credit {money(entry.creditBalanceAfter??0)}</small></div>
                </article>)}</div>}
            </section>
            <section className="khatha-panel">
              <div className="khatha-panel-heading"><div><p className="khatha-eyebrow">PAYMENT CONFIRMATION</p><h2>Repayment requests</h2></div></div>
              {repayments.length===0?<p className="khatha-help">No repayment requests yet.</p>:
                <div className="khatha-ledger">{repayments.map(r=><article key={r.id}>
                  <div><strong>{money(r.amount)} · {r.channel.replaceAll('_',' ')}</strong><small>{date(r.createdAt)}{r.reference?' · Ref '+r.reference:''}</small></div>
                  <div><span className={'khatha-status khatha-status--'+r.status.toLowerCase()}>{r.status}</span>{r.decisionReason&&<small>{r.decisionReason}</small>}</div>
                </article>)}</div>}
            </section>
          </div>}
        </div>
      </>}
  </section>
}
