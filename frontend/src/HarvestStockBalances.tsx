import type { HarvestListing } from './harvestApi'
import './HarvestStockBalances.css'

type StockListing = Pick<
  HarvestListing,
  | 'quantityKg'
  | 'availableQuantityKg'
  | 'reservedQuantityKg'
  | 'collectedQuantityKg'
>

const quantityFormat = new Intl.NumberFormat('en-LK', {
  maximumFractionDigits: 3,
})

function quantity(value: number | undefined): string {
  return typeof value === 'number' &&
    Number.isFinite(value) &&
    value >= 0
    ? `${quantityFormat.format(value)} kg`
    : '—'
}

export default function HarvestStockBalances({
  listing,
}: {
  listing: StockListing
}) {
  return (
    <dl className="harvest-stock-balances" aria-label="Stock balances">
      <div>
        <dt>Listed</dt>
        <dd>{quantity(listing.quantityKg)}</dd>
      </div>

      <div className="harvest-stock-available">
        <dt>Available</dt>
        <dd>{quantity(listing.availableQuantityKg)}</dd>
      </div>

      <div>
        <dt>Reserved</dt>
        <dd>{quantity(listing.reservedQuantityKg)}</dd>
      </div>

      <div>
        <dt>Collected</dt>
        <dd>{quantity(listing.collectedQuantityKg)}</dd>
      </div>
    </dl>
  )
}