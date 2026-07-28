/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref, ref} from 'vue'
import type {AttendanceEntry, AttendanceStatus} from '@/api/attendance'

export function useCheckMode(
    entries: Ref<AttendanceEntry[]>,
    setStatus: (entryId: number, status: AttendanceStatus) => Promise<void>,
) {
  const checkMode = ref(false)
  const checkIndex = ref(0)

  const uncheckedEntries = computed(() => entries.value.filter(e => e.status === 'UNCONFIRMED'))

  const currentCheckEntry = computed(() => {
    if (!checkMode.value) return null
    return uncheckedEntries.value[checkIndex.value] ?? null
  })

  function startCheckMode() {
    checkIndex.value = 0
    checkMode.value = true
  }

  async function checkSetStatus(status: AttendanceStatus) {
    if (!currentCheckEntry.value) return
    await setStatus(currentCheckEntry.value.id, status)
    if (checkIndex.value >= uncheckedEntries.value.length) {
      checkMode.value = false
    }
  }

  function skipCheck() {
    checkIndex.value++
    if (checkIndex.value >= uncheckedEntries.value.length) {
      checkMode.value = false
    }
  }

  return {
    checkMode,
    checkIndex,
    uncheckedEntries,
    currentCheckEntry,
    startCheckMode,
    checkSetStatus,
    skipCheck,
  }
}
