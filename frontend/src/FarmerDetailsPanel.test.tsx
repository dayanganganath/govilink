import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import FarmerDetailsPanel from './FarmerDetailsPanel'
import { ApiError, getFarmer, updateFarmer } from './api'
import type { Farmer } from './api'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()

  return {
    ...actual,
    getFarmer: vi.fn(),
    updateFarmer: vi.fn(),
  }
})

const getFarmerMock = vi.mocked(getFarmer)
const updateFarmerMock = vi.mocked(updateFarmer)

const originalFarmer: Farmer = {
  id: '87bc0103-f7fb-432f-a219-292e928164c7',
  fullName: 'Demo Farmer',
  phone: 'TEST-001',
  district: 'Matale',
  village: 'Original Village',
  active: true,
  version: 3,
  createdAt: '2026-10-19T10:00:00Z',
  updatedAt: '2026-10-20T10:00:00Z',
}

function renderPanel() {
  const onClose = vi.fn()
  const onSaved = vi.fn()
  const user = userEvent.setup()

  render(
    <FarmerDetailsPanel
      farmerId={originalFarmer.id}
      onClose={onClose}
      onSaved={onSaved}
    />,
  )

  return { user, onClose, onSaved }
}

beforeEach(() => {
  getFarmerMock.mockReset()
  updateFarmerMock.mockReset()
  getFarmerMock.mockResolvedValue({ ...originalFarmer })
})

describe('FarmerDetailsPanel', () => {
  it('loads the farmer and disables saving until details change', async () => {
    renderPanel()

    expect(
      await screen.findByRole('textbox', { name: /full name/i }),
    ).toHaveValue('Demo Farmer')

    expect(screen.getByRole('dialog')).toHaveAttribute('open')

    expect(getFarmerMock).toHaveBeenCalledWith(
      originalFarmer.id,
      expect.any(AbortSignal),
    )

    expect(screen.getByRole('textbox', { name: /phone/i }))
      .toHaveValue('TEST-001')

    expect(screen.getByRole('textbox', { name: /district/i }))
      .toHaveValue('Matale')

    expect(screen.getByRole('textbox', { name: /village/i }))
      .toHaveValue('Original Village')

    expect(screen.getByRole('button', { name: 'Save changes' }))
      .toBeDisabled()

    expect(updateFarmerMock).not.toHaveBeenCalled()
  })

  it('saves trimmed details with the loaded version', async () => {
    const updatedFarmer: Farmer = {
      ...originalFarmer,
      fullName: 'Updated Farmer',
      version: 4,
      updatedAt: '2026-10-21T10:00:00Z',
    }

    updateFarmerMock.mockResolvedValue(updatedFarmer)

    const { user, onSaved } = renderPanel()

    const fullName = await screen.findByRole('textbox', {
      name: /full name/i,
    })

    await user.clear(fullName)
    await user.type(fullName, '  Updated Farmer  ')
    await user.click(
      screen.getByRole('button', { name: 'Save changes' }),
    )

    await waitFor(() => {
      expect(updateFarmerMock).toHaveBeenCalledExactlyOnceWith(
        originalFarmer.id,
        {
          fullName: 'Updated Farmer',
          phone: 'TEST-001',
          district: 'Matale',
          village: 'Original Village',
          version: 3,
        },
      )
    })

    expect(
      await screen.findByText('Farmer details updated successfully.'),
    ).toBeInTheDocument()

    expect(onSaved).toHaveBeenCalledExactlyOnceWith(updatedFarmer)

    expect(fullName).toHaveValue('Updated Farmer')

    expect(screen.getByRole('button', { name: 'Save changes' }))
      .toBeDisabled()
  })

  it('preserves the draft and blocks another save after a conflict', async () => {
    updateFarmerMock.mockRejectedValue(
      new ApiError('This farmer was changed by another request.', 409),
    )

    const { user, onSaved } = renderPanel()

    const fullName = await screen.findByRole('textbox', {
      name: /full name/i,
    })

    await user.clear(fullName)
    await user.type(fullName, 'My unsaved draft')
    await user.click(
      screen.getByRole('button', { name: 'Save changes' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This farmer was changed by another request.',
    )

    expect(fullName).toHaveValue('My unsaved draft')

    expect(screen.getByRole('button', { name: 'Save changes' }))
      .toBeDisabled()

    await user.click(
      screen.getByRole('button', { name: 'Save changes' }),
    )

    expect(updateFarmerMock).toHaveBeenCalledTimes(1)
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('reloads after a conflict and uses the latest version on retry', async () => {
    const latestFarmer: Farmer = {
      ...originalFarmer,
      fullName: 'Changed in another tab',
      version: 4,
      updatedAt: '2026-10-21T10:00:00Z',
    }

    const updatedFarmer: Farmer = {
      ...latestFarmer,
      fullName: 'My revised update',
      version: 5,
      updatedAt: '2026-10-22T10:00:00Z',
    }

    getFarmerMock
      .mockResolvedValueOnce({ ...originalFarmer })
      .mockResolvedValueOnce(latestFarmer)

    updateFarmerMock
      .mockRejectedValueOnce(
        new ApiError('This farmer was changed by another request.', 409),
      )
      .mockResolvedValueOnce(updatedFarmer)

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)

    const { user, onSaved } = renderPanel()

    const fullName = await screen.findByRole('textbox', {
      name: /full name/i,
    })

    await user.clear(fullName)
    await user.type(fullName, 'Old draft')
    await user.click(
      screen.getByRole('button', { name: 'Save changes' }),
    )

    await screen.findByRole('alert')

    await user.click(
      screen.getByRole('button', { name: 'Reload latest details' }),
    )

    expect(confirm).toHaveBeenCalledWith(
      'Reload the latest details and discard your unsaved changes?',
    )

    const reloadedName = await screen.findByDisplayValue(
      'Changed in another tab',
    )

    expect(getFarmerMock).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Save changes' }))
      .toBeDisabled()

    await user.clear(reloadedName)
    await user.type(reloadedName, 'My revised update')

    expect(screen.getByRole('button', { name: 'Save changes' }))
      .toBeEnabled()

    await user.click(
      screen.getByRole('button', { name: 'Save changes' }),
    )

    await waitFor(() => {
      expect(updateFarmerMock).toHaveBeenLastCalledWith(
        originalFarmer.id,
        {
          fullName: 'My revised update',
          phone: 'TEST-001',
          district: 'Matale',
          village: 'Original Village',
          version: 4,
        },
      )

      expect(onSaved).toHaveBeenCalledExactlyOnceWith(updatedFarmer)
    })

    expect(updateFarmerMock).toHaveBeenCalledTimes(2)
  })

  it('keeps unsaved changes when closing is cancelled', async () => {
    const confirm = vi.spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)

    const { user, onClose } = renderPanel()

    const fullName = await screen.findByRole('textbox', {
      name: /full name/i,
    })

    await user.clear(fullName)
    await user.type(fullName, 'Unsaved name')

    await user.click(screen.getByRole('button', { name: 'Close' }))

    expect(confirm).toHaveBeenCalledWith(
      'Discard your unsaved changes and close?',
    )

    expect(onClose).not.toHaveBeenCalled()
    expect(fullName).toHaveValue('Unsaved name')

    await user.click(screen.getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(updateFarmerMock).not.toHaveBeenCalled()
  })
})