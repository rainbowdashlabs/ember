/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {showToast} from '@/util/toast'
import {describeFailure} from '@/util/failure'
import {shiftIsHeld} from '@/util/modifierKeys'

/** A question asked before something that cannot be taken back, with what answering yes does. */
export interface Confirmation {
  title: string
  message: string
  label: string
  run: () => Promise<void>
}

/**
 * The one question a conversation asks before blocking, leaving, deleting or moderating.
 *
 * <p>Shift held as the step is started takes it at once, as it does for every plain confirmation.
 * A refusal is shown by a toast, since the pane has no alert of its own.
 */
export function useConversationConfirm() {
  const {t} = useI18n()
  const confirmation = ref<Confirmation | null>(null)
  const confirming = ref(false)

  /** Asks before the step, or takes it at once while shift is held. */
  function ask(question: Confirmation) {
    confirmation.value = question
    if (shiftIsHeld()) {
      void confirm()
      return
    }
    confirming.value = true
  }

  /** Takes the step that was asked about. */
  async function confirm() {
    const question = confirmation.value
    confirming.value = false
    if (!question) return
    try {
      await question.run()
    } catch (e) {
      showToast(describeFailure(e, t).message, 'error')
    }
  }

  return {confirmation, confirming, ask, confirm}
}
