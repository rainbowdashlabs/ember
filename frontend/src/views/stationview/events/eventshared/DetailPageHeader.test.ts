/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {h} from 'vue'
import DetailPageHeader from './DetailPageHeader.vue'

enableAutoUnmount(afterEach)

/** Lets the menu panel and the dialog settle: both place their focus a tick after they mount. */
async function settle() {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 0))
  await flushPromises()
}

function inPage(testId: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)
}

async function mountHeader(props: Record<string, unknown>, entries = false) {
  const header = await mountSuspended(DetailPageHeader, {
    attachTo: document.body,
    props: {backTo: {name: 'events'}, editLabel: 'Bearbeiten', editTestId: 'page-edit', menuTestId: 'page-actions', ...props},
    slots: entries ? {default: () => h('button', {'data-testid': 'page-copy'}, 'Kopieren')} : {},
  })
  await settle()
  return header
}

async function openMenu() {
  inPage('page-actions-trigger')!.click()
  await settle()
}

/** Both detail pages share one head: Back, the primary Edit and one menu ending in Delete. */
describe('DetailPageHeader', () => {
  it('shows only Back to a reader who may neither edit nor delete', async () => {
    await mountHeader({editTo: null, remove: null})

    expect(inPage('detail-page-back')).not.toBeNull()
    expect(inPage('page-edit')).toBeNull()
    expect(inPage('page-actions-trigger')).toBeNull()
  })

  it('offers Edit and puts Delete last in the menu, after the entries of the page', async () => {
    await mountHeader({editTo: {name: 'events'}, remove: vi.fn(), deleteTestId: 'page-delete'}, true)

    expect(inPage('page-edit')?.textContent).toContain('Bearbeiten')
    await openMenu()
    const entries = [...document.querySelectorAll('[data-testid="page-actions"] [data-testid]')]
        .map(element => element.getAttribute('data-testid'))
        .filter(testId => testId?.startsWith('page-'))
    expect(entries).toEqual(['page-copy', 'page-delete'])
  })

  it('shows the menu for its entries alone where the reader may not delete', async () => {
    await mountHeader({editTo: {name: 'events'}, remove: null, hasMenuItems: true, deleteTestId: 'page-delete'}, true)

    await openMenu()
    expect(inPage('page-copy')).not.toBeNull()
    expect(inPage('page-delete')).toBeNull()
  })

  it('asks before it deletes', async () => {
    const remove = vi.fn(async () => undefined)
    await mountHeader({editTo: null, remove, deleteTestId: 'page-delete', deleteMessage: 'Wirklich löschen?'})

    await openMenu()
    inPage('page-delete')!.click()
    await settle()
    expect(remove).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Wirklich löschen?')

    const confirm = [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')]
        .find(button => button.textContent?.includes('Löschen'))!
    confirm.click()
    await settle()
    expect(remove).toHaveBeenCalledOnce()
  })

  it('closes the question and says why where deleting is refused', async () => {
    const remove = vi.fn(async () => {
      throw new Error('refused')
    })
    await mountHeader({editTo: null, remove, deleteTestId: 'page-delete', deleteMessage: 'Wirklich löschen?'})

    await openMenu()
    inPage('page-delete')!.click()
    await settle()
    const confirm = [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')]
        .find(button => button.textContent?.includes('Löschen'))!
    confirm.click()
    await settle()

    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.querySelector('[role="alert"]')).not.toBeNull()
  })
})
