/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance} from '@/api'
import type {AttendanceSession} from '@/api/attendance'
import {localInputToInstant} from '@/util/format'

/**
 * The sheet's own title, time frame and worth.
 *
 * <p>The times are the sheet's from the moment it is made: an appointment hands them down once, and
 * every correction afterwards belongs to the sheet alone. Each moment is written whole, day and
 * time together, because a sheet may run over a weekend and a time on its own would name two of
 * them.
 */
export function useSessionMeta(
    sessionId: Ref<number>,
    session: Ref<AttendanceSession | null>,
    error: Ref<string>,
) {
  const {t} = useI18n()

  let sessionSaveTimer: ReturnType<typeof setTimeout> | null = null

  function saveSessionDebounced() {
    if (sessionSaveTimer) clearTimeout(sessionSaveTimer)
    sessionSaveTimer = setTimeout(saveSessionMeta, 500)
  }

  async function saveSessionMeta() {
    if (!session.value) return
    error.value = ''
    try {
      const s = session.value
      await attendance.updateSession(sessionId.value, {
        startTime: s.startTime,
        endTime: s.endTime,
        title: s.title,
        countedMinutes: s.countedMinutes ?? null,
      })
    } catch {
      error.value = t('common.error')
    }
  }

  function setSessionStartTime(moment: string) {
    const instant = localInputToInstant(moment)
    if (!session.value || !instant) return
    session.value = {...session.value, startTime: instant}
    saveSessionMeta()
  }

  function setSessionEndTime(moment: string) {
    const instant = localInputToInstant(moment)
    if (!session.value || !instant) return
    session.value = {...session.value, endTime: instant}
    saveSessionMeta()
  }

  function setSessionTitle(title: string) {
    if (!session.value) return
    session.value = {...session.value, title}
    saveSessionDebounced()
  }

  /** What a whole presence at this sheet counts as, in hours, or nothing to let the times decide. */
  function setCountedHours(hours: number | null) {
    if (!session.value) return
    const minutes = hours === null || Number.isNaN(hours) ? null : Math.round(hours * 60)
    session.value = {...session.value, countedMinutes: minutes}
    saveSessionDebounced()
  }

  /** Takes the appointment's time frame back, for a sheet that was moved off it by mistake. */
  function takeEventTimes(startTime: string, endTime: string) {
    if (!session.value) return
    session.value = {...session.value, startTime, endTime}
    saveSessionMeta()
  }

  return {
    setSessionStartTime,
    setSessionEndTime,
    setSessionTitle,
    setCountedHours,
    takeEventTimes,
  }
}
