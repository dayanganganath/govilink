import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from './api'
import HarvestStatusActions from './HarvestStatusActions'
import { updateHarvestStatus } from './harvestApi'
import type { HarvestListing } from './harvestApi'

vi.mock('./harvestApi', () => ({
  updateHarvestStatus: vi.fn(),
}))

const updateStatusMock = vi.mocked(updateHarvestStatus)

const listing: HarvestListing = {
  id: '56e21060-f3f5-4c77-aa7e-fdbd82ba791d',
  farmerId: '87bc0103-f7fb-432f-a219-292e928164c7',
  farmerName: 'Demo Farmer',
  district: 'Matale',
  cropName: 'Tomato',
  quantityKg: 125.5,
  expectedPricePerKg: 180,
  availableFrom: '2026-10-27',
  status: 'AVAILABLE',
  version: 3,
  createdAt: '2026-10-27T04:00:00Z',
  updatedAt: '2026-10-27T04:00:00Z',
}

function renderActions(record: HarvestListing = listing) {
  const onChanged = vi.fn()
  const onReload = vi.fn()
  const user = userEvent.setup()

  render(
    <HarvestStatusActions
      listing={record}
      onChanged={onChanged}
      onReload={onReload}
    />,
  )

  return { user, onChanged, onReload }
}

beforeEach(() => {
  updateStatusMock.mockReset()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})

describe('HarvestStatusActions', () => {
  it.each([
    {
      target: 'SOLD' as const,
      button: 'Mark Tomato as sold',
    },
    {
      target: 'CANCELLED' as const,
      button: 'Cancel Tomato listing',
    },
  ])('updates to $target with the displayed version', async ({
    target,
    button,
  }) => {
    const updated: HarvestListing = {
      ...listing,
      status: target,
      version: 4,
    }

    updateStatusMock.mockResolvedValue(updated)

    const { user, onChanged, onReload } = renderActions()

    await user.click(screen.getByRole('button', { name: button }))

    expect(window.confirm).toHaveBeenCalledTimes(1)

    expect(updateStatusMock).toHaveBeenCalledExactlyOnceWith(
      listing.id,
      target,
      3,
    )

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledExactlyOnceWith(updated)
    })

    expect(onReload).not.toHaveBeenCalled()
  })

  it('does not send an update when confirmation is declined', async () => {
    vi.mocked(window.confirm).mockReturnValue(false)

    const { user, onChanged } = renderActions()

    await user.click(
      screen.getByRole('button', { name: 'Mark Tomato as sold' }),
    )

    expect(updateStatusMock).not.toHaveBeenCalled()
    expect(onChanged).not.toHaveBeenCalled()

    expect(screen.getByRole('button', { name: 'Mark Tomato as sold' }))
      .toBeEnabled()
  })

  it.each(['SOLD', 'CANCELLED'] as const)(
    'does not show update buttons for a %s listing',
    (status) => {
      renderActions({ ...listing, status })

      expect(screen.getByText('Closed listing')).toBeInTheDocument()

      expect(screen.queryByRole('button', {
        name: 'Mark Tomato as sold',
      })).not.toBeInTheDocument()

      expect(screen.queryByRole('button', {
        name: 'Cancel Tomato listing',
      })).not.toBeInTheDocument()

      expect(updateStatusMock).not.toHaveBeenCalled()
    },
  )

  it.each([409, 404])(
    'blocks another update after HTTP %s and offers reload',
    async (status) => {
      updateStatusMock.mockRejectedValue(
        new ApiError('Reload the latest harvest listing.', status),
      )

      const { user, onChanged, onReload } = renderActions()

      await user.click(
        screen.getByRole('button', { name: 'Mark Tomato as sold' }),
      )

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Reload the latest harvest listing.',
      )

      expect(screen.getByRole('button', { name: 'Mark Tomato as sold' }))
        .toBeDisabled()

      expect(screen.getByRole('button', { name: 'Cancel Tomato listing' }))
        .toBeDisabled()

      await user.click(
        screen.getByRole('button', { name: 'Cancel Tomato listing' }),
      )

      expect(updateStatusMock).toHaveBeenCalledTimes(1)
      expect(onChanged).not.toHaveBeenCalled()

      await user.click(
        screen.getByRole('button', { name: 'Reload latest listings' }),
      )

      expect(onReload).toHaveBeenCalledTimes(1)
    },
  )

  it('prevents duplicate updates while a request is pending', async () => {
    let resolveRequest!: (value: HarvestListing) => void

    const pending = new Promise<HarvestListing>((resolve) => {
      resolveRequest = resolve
    })

    updateStatusMock.mockReturnValue(pending)

    const { user, onChanged } = renderActions()

    await user.click(
      screen.getByRole('button', { name: 'Mark Tomato as sold' }),
    )

    expect(screen.getByRole('status')).toHaveTextContent(
      'Updating status…',
    )

    expect(screen.getByRole('button', { name: 'Mark Tomato as sold' }))
      .toBeDisabled()

    expect(screen.getByRole('button', { name: 'Cancel Tomato listing' }))
      .toBeDisabled()

    await user.click(
      screen.getByRole('button', { name: 'Mark Tomato as sold' }),
    )

    await user.click(
      screen.getByRole('button', { name: 'Cancel Tomato listing' }),
    )

    expect(updateStatusMock).toHaveBeenCalledTimes(1)
    expect(window.confirm).toHaveBeenCalledTimes(1)

    const updated: HarvestListing = {
      ...listing,
      status: 'SOLD',
      version: 4,
    }

    await act(async () => {
      resolveRequest(updated)
      await pending
    })

    expect(onChanged).toHaveBeenCalledExactlyOnceWith(updated)
  })

  it('allows retry after a network failure', async () => {
    const updated: HarvestListing = {
      ...listing,
      status: 'SOLD',
      version: 4,
    }

    updateStatusMock
      .mockRejectedValueOnce(new Error('Network unavailable.'))
      .mockResolvedValueOnce(updated)

    const { user, onChanged } = renderActions()

    await user.click(
      screen.getByRole('button', { name: 'Mark Tomato as sold' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Network unavailable.',
    )

    expect(screen.getByRole('button', { name: 'Mark Tomato as sold' }))
      .toBeEnabled()

    expect(onChanged).not.toHaveBeenCalled()

    await user.click(
      screen.getByRole('button', { name: 'Mark Tomato as sold' }),
    )

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledExactlyOnceWith(updated)
    })

    expect(updateStatusMock).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})