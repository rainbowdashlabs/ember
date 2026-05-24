/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance} from '@/api'
import type {AttendanceSession} from '@/api/types'

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
      })
    } catch {
      error.value = t('common.error')
    }
  }

  function setSessionStartTime(time: string) {
    if (!session.value || !time) return
    const today = new Date().toISOString().slice(0, 10)
    session.value = {...session.value, startTime: new Date(`${today}T${time}:00`).toISOString()}
    saveSessionMeta()
  }

  function setSessionEndTime(time: string) {
    if (!session.value || !time) return
    const today = new Date().toISOString().slice(0, 10)
    session.value = {...session.value, endTime: new Date(`${today}T${time}:00`).toISOString()}
    saveSessionMeta()
  }

  function setSessionTitle(title: string) {
    if (!session.value) return
    session.value = {...session.value, title}
    saveSessionDebounced()
  }

  return {
    setSessionStartTime,
    setSessionEndTime,
    setSessionTitle,
  }
}
