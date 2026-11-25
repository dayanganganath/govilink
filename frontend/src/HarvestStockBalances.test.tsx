import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import HarvestStockBalances from './HarvestStockBalances'
import { hasAvailableHarvestStock } from './harvestStock'

afterEach(cleanup)

function balance(label: string): HTMLElement {
  const value = screen.getByText(label).parentElement?.querySelector('dd')

  if (!value) {
    throw new Error(`Missing balance: ${label}`)
  }

  return value as HTMLElement
}

describe('Harvest stock balances', () => {
  it('separates listed, available, reserved and collected quantities', () => {
    render(
      <HarvestStockBalances listing={{
        quantityKg: 100,
        availableQuantityKg: 40,
        reservedQuantityKg: 35,
        collectedQuantityKg: 25,
      }} />,
    )

    expect(balance('Listed')).toHaveTextContent('100 kg')
    expect(balance('Available')).toHaveTextContent('40 kg')
    expect(balance('Reserved')).toHaveTextContent('35 kg')
    expect(balance('Collected')).toHaveTextContent('25 kg')
  })

  it('shows no available stock when everything has been collected', () => {
    render(
      <HarvestStockBalances listing={{
        quantityKg: 100,
        availableQuantityKg: 0,
        reservedQuantityKg: 0,
        collectedQuantityKg: 100,
      }} />,
    )

    expect(balance('Listed')).toHaveTextContent('100 kg')
    expect(balance('Available')).toHaveTextContent('0 kg')
    expect(balance('Collected')).toHaveTextContent('100 kg')
  })

  it('preserves fractional quantities', () => {
    render(
      <HarvestStockBalances listing={{
        quantityKg: 10.125,
        availableQuantityKg: 4.025,
        reservedQuantityKg: 3.1,
        collectedQuantityKg: 3,
      }} />,
    )

    expect(balance('Listed')).toHaveTextContent('10.125 kg')
    expect(balance('Available')).toHaveTextContent('4.025 kg')
    expect(balance('Reserved')).toHaveTextContent('3.1 kg')
  })

  it('shows unknown balances without inventing zero or available stock', () => {
    render(
      <HarvestStockBalances listing={{ quantityKg: 100 }} />,
    )

    expect(balance('Listed')).toHaveTextContent('100 kg')
    expect(balance('Available')).toHaveTextContent('—')
    expect(balance('Reserved')).toHaveTextContent('—')
    expect(balance('Collected')).toHaveTextContent('—')
  })

  it('does not count a fully reserved listing as available stock', () => {
    expect(hasAvailableHarvestStock({
      status: 'AVAILABLE',
      availableQuantityKg: 0,
    })).toBe(false)

    expect(hasAvailableHarvestStock({
      status: 'AVAILABLE',
      availableQuantityKg: 10,
    })).toBe(true)
  })

  it('does not count closed listings or unknown balances as available stock', () => {
    expect(hasAvailableHarvestStock({
      status: 'SOLD',
      availableQuantityKg: 10,
    })).toBe(false)

    expect(hasAvailableHarvestStock({
      status: 'CANCELLED',
      availableQuantityKg: 10,
    })).toBe(false)

    expect(hasAvailableHarvestStock({
      status: 'AVAILABLE',
    })).toBe(false)
  })
})
