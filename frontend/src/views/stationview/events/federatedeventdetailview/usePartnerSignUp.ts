/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import type {EventRegistrationField, EventRegistrationFieldValue} from '@/api/generated/schema'

/**
 * Signing a member up for a partner's appointment, asking its questions first where it has any.
 *
 * <p>Without questions the sign-up goes out at once. With questions the member is held while the form
 * is open, and the sign-up goes out with the answers once it is confirmed; closing the form sends
 * nothing. A sign-up that is refused keeps the form open with the answers given, so they can be
 * corrected; `register` rejects to signal this.
 */
export function usePartnerSignUp(
    questions: Ref<EventRegistrationField[]>,
    register: (uid: string, answers: EventRegistrationFieldValue[]) => Promise<unknown>,
) {
  const asking = ref<string | null>(null)

  const open = computed({
    get: () => asking.value !== null,
    set: shown => {
      if (!shown) asking.value = null
    },
  })

  /** Starts signing a member up. */
  async function start(uid: string) {
    if (questions.value.length === 0) {
      await register(uid, [])
      return
    }
    asking.value = uid
  }

  /** Sends the sign-up of the member the form was opened for, with the answers given. */
  async function answer(values: EventRegistrationFieldValue[]) {
    const uid = asking.value
    if (uid === null) return
    try {
      await register(uid, values)
    } catch {
      return
    }
    asking.value = null
  }

  return {open, start, answer}
}
