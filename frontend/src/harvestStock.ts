import type { HarvestListing } from './harvestApi'

export function hasAvailableHarvestStock(
  listing: Pick<HarvestListing, 'status' | 'availableQuantityKg'>,
): boolean {
  return listing.status === 'AVAILABLE' &&
    typeof listing.availableQuantityKg === 'number' &&
    Number.isFinite(listing.availableQuantityKg) &&
    listing.availableQuantityKg > 0
}