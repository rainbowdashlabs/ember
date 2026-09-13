/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CustodyChoice from './CustodyChoice.vue'
import {movements} from '@/api'
import type {ItemCustodyName} from '@/api/inventory'
import {MovementState, type MovementStateName} from '@/api/movements'
import {apiErrorMessage} from '@/util/apiError'

/**
 * Putting a movement where somebody says it should have been.
 *
 * <p>There is no status to set: where a movement stands is read off its two pieces. So the form asks
 * where the pieces are, and the chain follows to whichever step that world has not reached. The log
 * keeps the reason and says the movement was corrected rather than walked.
 */
const props = defineProps<{
  movementId: number
}>()

const emit = defineEmits<{
  done: []
}>()

const model = defineModel<boolean>({required: true})

const {t} = useI18n()

const closeStates: MovementStateName[] = [MovementState.DONE, MovementState.DECLINED, MovementState.CANCELLED]

const outgoing = ref('')
const incoming = ref('')
const detach = ref(false)
const closeAs = ref('')
const reason = ref('')
const busy = ref(false)
const error = ref('')

watch(model, open => {
  if (!open) return
  outgoing.value = ''
  incoming.value = ''
  detach.value = false
  closeAs.value = ''
  reason.value = ''
  error.value = ''
})

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await movements.correctMovement(props.movementId, {
      outgoing: outgoing.value ? (outgoing.value as ItemCustodyName) : null,
      incoming: incoming.value ? (incoming.value as ItemCustodyName) : null,
      detachArrival: detach.value,
      closeAs: closeAs.value ? (closeAs.value as MovementStateName) : null,
      reason: reason.value,
    })
    model.value = false
    emit('done')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4" data-testid="movement-correct-modal">
      <SubHeader>{{ t('movements.queue.correctPanel.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('movements.queue.correctPanel.hint') }}</MutedText>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div class="space-y-1">
        <CustodyChoice v-model="outgoing" :label="t('movements.queue.correctPanel.outgoing')"
                       testid="correct-outgoing"/>
      </div>

      <div class="space-y-1">
        <CustodyChoice v-model="incoming" :disabled="detach" :label="t('movements.queue.correctPanel.incoming')"
                       testid="correct-incoming"/>
        <CheckboxInput v-model="detach" data-testid="correct-detach">
          {{ t('movements.queue.correctPanel.detach') }}
        </CheckboxInput>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('movements.queue.correctPanel.closeAs') }}</FieldLabel>
        <SelectInput v-model="closeAs" data-testid="correct-close">
          <option value="">{{ t('movements.queue.correctPanel.stayOpen') }}</option>
          <option v-for="state in closeStates" :key="state" :value="state">
            {{ t(`movements.state.${state}`) }}
          </option>
        </SelectInput>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('movements.queue.correctPanel.reason') }}</FieldLabel>
        <TextInput v-model="reason" data-testid="correct-reason"
                   :placeholder="t('movements.queue.correctPanel.reasonPlaceholder')"/>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="model = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="busy || !reason.trim()" data-testid="correct-confirm" @click="submit">
          {{ t('movements.queue.correctPanel.confirm') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
