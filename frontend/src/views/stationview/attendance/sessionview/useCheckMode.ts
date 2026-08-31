/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref, ref} from 'vue'
import type {AttendanceStatus} from '@/api/attendance'

/**
 * One name on the sheet that is still open.
 *
 * <p>Somebody the template expects but who has no entry yet counts as open too: they stand on the
 * sheet with nothing recorded against them, which is exactly what the walk is for. Their entry is
 * written the moment they are marked, so nothing is recorded about anybody who is never reached.
 */
export interface CheckRow {
    memberId: number
    entryId: number | null
}

export function useCheckMode(
    openRows: Ref<CheckRow[]>,
    mark: (row: CheckRow, status: AttendanceStatus) => Promise<void>,
) {
  const checkMode = ref(false)
  const checkIndex = ref(0)

  const currentCheckRow = computed(() => {
    if (!checkMode.value) return null
    return openRows.value[checkIndex.value] ?? null
  })

  function startCheckMode() {
    checkIndex.value = 0
    checkMode.value = true
  }

  async function checkSetStatus(status: AttendanceStatus) {
    const row = currentCheckRow.value
    if (!row) return
    await mark(row, status)
    if (checkIndex.value >= openRows.value.length) {
      checkMode.value = false
    }
  }

  function skipCheck() {
    checkIndex.value++
    if (checkIndex.value >= openRows.value.length) {
      checkMode.value = false
    }
  }

  return {
    checkMode,
    checkIndex,
    openRows,
    currentCheckRow,
    startCheckMode,
    checkSetStatus,
    skipCheck,
  }
}
