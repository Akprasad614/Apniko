import type { ButtonHTMLAttributes, ReactNode } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: 'dark' | 'light' | 'quiet'
  children: ReactNode
}

export function Button({ tone = 'dark', className = '', children, ...props }: ButtonProps) {
  return (
    <button className={`button button--${tone} ${className}`.trim()} {...props}>
      {children}
    </button>
  )
}
