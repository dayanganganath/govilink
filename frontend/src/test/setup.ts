import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Simulate opening and closing a dialog in the test DOM.
// Browser focus management is outside the scope of these tests.
Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
  configurable: true,
  value: function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  },
})

Object.defineProperty(HTMLDialogElement.prototype, 'close', {
  configurable: true,
  value: function (this: HTMLDialogElement) {
    this.removeAttribute('open')
  },
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})