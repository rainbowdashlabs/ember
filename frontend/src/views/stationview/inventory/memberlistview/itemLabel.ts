/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { InventoryItem } from '@/api/inventory'

/** Which parts of a piece the list names it by, and the words for its sizes. */
export interface ItemLabelParts {
  showName: boolean
  showInternalId: boolean
  showSize: boolean
  sizeMap: ReadonlyMap<number, string>
}

/** The name and the internal id of a piece, as far as the list shows them. */
export function itemNamePart(item: InventoryItem, parts: ItemLabelParts): string {
  const words: string[] = []
  if (parts.showName && item.name) words.push(item.name)
  if (parts.showInternalId && item.internalId) words.push(`(${item.internalId})`)
  return words.join(' ')
}

/** The size of a piece, where the list shows sizes and the piece has one. */
export function itemSizeLabel(item: InventoryItem, parts: ItemLabelParts): string {
  if (!parts.showSize || !item.sizeId) return ''
  return parts.sizeMap.get(item.sizeId) ?? ''
}

/** A piece in plain words, which is what its column sorts and filters by and what the CSV holds. */
export function itemLabel(item: InventoryItem, parts: ItemLabelParts): string {
  const label = [itemNamePart(item, parts), itemSizeLabel(item, parts)].filter(Boolean).join(' ')
  return label || item.name || '-'
}
