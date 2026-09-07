/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import StockHint from './StockHint.vue'

function hint(stock: number, quantity: number) {
  return mount(StockHint, {props: {stock, quantity}})
}

describe('StockHint', () => {
  it('says how many pieces there are', () => {
    expect(hint(6, 4).get('[data-testid="line-target-stock"]').text()).toBe('Vorhanden: 6 Stück')
  })

  it('says plainly when there is none rather than writing a zero', () => {
    expect(hint(0, 1).get('[data-testid="line-target-stock"]').text())
        .toBe('Davon ist gerade nichts an der Wache.')
  })

  it('stays quiet while the line asks for no more than there is', () => {
    expect(hint(6, 6).find('[data-testid="line-target-short"]').exists()).toBe(false)
  })

  it('reports asking for more than there is, naming both numbers', () => {
    const message = hint(6, 9).get('[data-testid="line-target-short"]').text()

    expect(message).toContain('9')
    expect(message).toContain('6')
  })

  it('reports rather than refuses, so the line can still be written', () => {
    const wrapper = hint(6, 9)

    expect(wrapper.find('[data-testid="line-target-short"]').exists()).toBe(true)
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
