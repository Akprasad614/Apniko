import type { InputHTMLAttributes } from 'react'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
}

export function Field({ label, hint, id, ...props }: FieldProps) {
  const inputId = id ?? props.name
  return (
    <div className="field">
      <label className="field__label" htmlFor={inputId}>{label}</label>
      <input className="field__input" id={inputId} {...props} />
      {hint && <p className="field__hint">{hint}</p>}
    </div>
  )
}
