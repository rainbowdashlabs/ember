/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onUnmounted, ref, type Ref } from 'vue'

/**
 * Counts the time elapsed since the attempt was started and reports back once
 * the time limit of the test is used up.
 */
export function useQuizTimer(
    timeLimit: Ref<number | null>,
    startedAt: Ref<string | null>,
    onExpired: () => void,
) {
  const timerSeconds = ref(0)
  const timerInterval = ref<ReturnType<typeof setInterval> | null>(null)

  const timerDisplay = computed(() => {
    if (!timeLimit.value) return ''
    const remaining = Math.max(0, (timeLimit.value * 60) - timerSeconds.value)
    const minutes = Math.floor(remaining / 60)
    const seconds = remaining % 60
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  })

  const timerExpired = computed(() => {
    if (!timeLimit.value) return false
    return timerSeconds.value >= timeLimit.value * 60
  })

  function stopTimer() {
    if (timerInterval.value) {
      clearInterval(timerInterval.value)
      timerInterval.value = null
    }
  }

  function startTimer() {
    if (timerInterval.value) return
    if (!timeLimit.value) return
    if (startedAt.value === null) return

    const startedMillis = new Date(startedAt.value).getTime()
    timerSeconds.value = Math.floor((Date.now() - startedMillis) / 1000)

    timerInterval.value = setInterval(() => {
      timerSeconds.value++
      if (timerExpired.value) {
        stopTimer()
        onExpired()
      }
    }, 1000)
  }

  onUnmounted(stopTimer)

  return {
    timerDisplay,
    timerExpired,
    startTimer,
    stopTimer,
  }
}
