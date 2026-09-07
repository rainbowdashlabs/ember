/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ItemCustody, ItemOwner, type InventoryItem} from '@/api/inventory'
import {isAtHand, stockByArt, stockByInventory} from './inventoryStock'

function piece(id: number, patch: Partial<InventoryItem> = {}): InventoryItem {
  return {
    id,
    inventoryId: 1,
    artId: 7,
    custody: ItemCustody.AT_STATION,
    ownerKind: ItemOwner.STATION,
    ...patch,
  }
}

describe('isAtHand', () => {
  it('counts a piece standing at the station', () => {
    expect(isAtHand(piece(1))).toBe(true)
  })

  it('counts a piece a member keeps, because that is the ordinary state of a radio', () => {
    expect(isAtHand(piece(1, {custody: ItemCustody.WITH_MEMBER, assignedTo: 4}))).toBe(true)
  })

  it('counts a piece resting with the station as its owner', () => {
    expect(isAtHand(piece(1, {custody: ItemCustody.WITH_OWNER, ownerKind: ItemOwner.STATION}))).toBe(true)
  })

  it('leaves out a piece resting with an owner that is not the station', () => {
    expect(isAtHand(piece(1, {custody: ItemCustody.WITH_OWNER, ownerKind: ItemOwner.PARTNER_STATION}))).toBe(false)
  })

  it('leaves out what is somewhere else', () => {
    expect(isAtHand(piece(1, {custody: ItemCustody.LOST}))).toBe(false)
    expect(isAtHand(piece(1, {custody: ItemCustody.IN_TRANSIT}))).toBe(false)
    expect(isAtHand(piece(1, {custody: ItemCustody.WITH_PARTNER}))).toBe(false)
  })

  it('leaves out a piece nobody wrote a custody for', () => {
    expect(isAtHand(piece(1, {custody: null}))).toBe(false)
  })
})

describe('stockByArt', () => {
  it('counts the pieces of each kind that are at hand', () => {
    const counts = stockByArt([
      piece(1, {artId: 7}),
      piece(2, {artId: 7, custody: ItemCustody.WITH_MEMBER}),
      piece(3, {artId: 7, custody: ItemCustody.LOST}),
      piece(4, {artId: 8}),
    ])
    expect(counts.get(7)).toBe(2)
    expect(counts.get(8)).toBe(1)
  })

  it('leaves a kind nothing is filed under out rather than reporting zero', () => {
    expect(stockByArt([piece(1, {artId: 7, custody: ItemCustody.LOST})]).has(7)).toBe(false)
  })

  it('ignores pieces nobody gave a kind to', () => {
    expect(stockByArt([piece(1, {artId: null})]).size).toBe(0)
  })
})

describe('stockByInventory', () => {
  it('counts the pieces of each inventory that are at hand', () => {
    const counts = stockByInventory([
      piece(1, {inventoryId: 2}),
      piece(2, {inventoryId: 2, custody: ItemCustody.WITH_PARTNER}),
      piece(3, {inventoryId: 3}),
    ])
    expect(counts.get(2)).toBe(1)
    expect(counts.get(3)).toBe(1)
  })
})
