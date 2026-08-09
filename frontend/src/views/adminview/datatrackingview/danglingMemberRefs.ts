/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { DataTracking } from '@/api/dataTracking'
import type { DanglingRef } from './DanglingRefAudit.vue'

const MEMBER_TABLE = 'station_member'

/**
 * Finds member references that deleting a member would leave behind.
 *
 * A table declared as holding member data is expected to reach the member through a foreign key,
 * so removing the member cascades. Where that key is missing — or points somewhere else, or the
 * declared column does not exist at all — the reference survives the deletion, which is a data
 * protection problem rather than a cosmetic one.
 *
 * The member table itself is skipped: its own key is the member, not a reference to one.
 */
export function findDanglingMemberRefs(tracking: DataTracking | null): DanglingRef[] {
  if (!tracking) return []
  const out: DanglingRef[] = []

  for (const [table, entry] of Object.entries(tracking.tables)) {
    if (entry.gdprExport?.status !== 'TRACKED') continue

    for (const identity of entry.gdprExport.identityColumns ?? []) {
      if (identity.type !== 'MEMBER_ID') continue

      if (!entry.columns.some(c => c.name === identity.column)) {
        out.push({
          table,
          column: identity.column,
          identityType: identity.type,
          hint: 'column does not exist on the table',
        })
        continue
      }

      if (table === MEMBER_TABLE) continue

      const foreignKeys = entry.foreignKeys ?? []
      if (foreignKeys.some(fk => fk.column === identity.column && fk.refTable === MEMBER_TABLE)) continue

      const other = foreignKeys.find(fk => fk.column === identity.column)
      out.push({
        table,
        column: identity.column,
        identityType: identity.type,
        hint: other ? `FK points to ${other.refTable} instead of ${MEMBER_TABLE}` : 'no FK at all',
      })
    }
  }

  return out.sort((a, b) => a.table.localeCompare(b.table) || a.column.localeCompare(b.column))
}
