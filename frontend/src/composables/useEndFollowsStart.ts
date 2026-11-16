/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Ref, watch} from 'vue'
import {endFollowingStart, instantToLocalInput} from '@/util/format'

/**
 * Moves an end along with its start, so a span keeps its length when only its start is changed. Both
 * refs hold `datetime-local` values.
 *
 * <p>Changing only the day of an appointment used to leave the end on the old day, and the form then
 * refused the appointment for ending before it begins until the end was changed as well.
 *
 * <p>For state the caller owns, not for props: a reload may change both props at once. The watch runs
 * synchronously, so code that sets a start and then its own end keeps the end it set.
 *
 * @param start the start the end follows
 * @param end   the end that moves with it
 */
export function useEndFollowsStart(start: Ref<string | undefined>, end: Ref<string | undefined>) {
  const follow = endFollower(end)
  watch(start, (now, before) => follow(before, now), {flush: 'sync'})
}

/**
 * The step that moves an end after its start was changed.
 *
 * <p>The start is followed from the last moment that was a whole date. A year typed digit by digit
 * passes through years like 0002 and 0202 on the way to 2027, and moving the end through those would
 * leave it somewhere no calendar shows.
 *
 * @param end the end that moves with its start
 * @returns a step taking the start before and after a change
 */
function endFollower(end: Ref<string | undefined>): (before?: string, now?: string) => void {
  let anchor = ''
  return (before, now) => {
    if (wholeMoment(before)) anchor = before
    if (!wholeMoment(now)) return
    const from = anchor
    anchor = now
    if (!from || !end.value) return
    const moved = endFollowingStart(from, now, end.value)
    if (moved) end.value = instantToLocalInput(moved)
  }
}

/** Whether a value names a moment with a four-digit year, rather than one caught halfway through typing. */
function wholeMoment(value?: string): value is string {
  return !!value && Number(value.slice(0, 4)) >= 1000 && !Number.isNaN(new Date(value).getTime())
}
