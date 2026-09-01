/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {exchanges} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {apiErrorMessage} from '@/util/apiError'

/**
 * Putting an exchange where it should have been, in either direction.
 *
 * <p>Unlike advancing one, this is not a step somebody walked: it is a record being set right. It
 * therefore asks for a reason rather than an optional note, offers every status including the ends,
 * and says plainly that the pieces are moved to match and that nobody is notified.
 */
const props = defineProps<{
  request: ExchangeRequestEntry
}>()

const emit = defineEmits<{
  done: []
  cancel: []
  error: [msg: string]
}>()

const {t} = useI18n()

const everyStatus: ExchangeStatusName[] = [
  ExchangeStatus.ANNOUNCED,
  ExchangeStatus.RECEIVED,
  ExchangeStatus.SHIPPED,
  ExchangeStatus.ARRIVED,
  ExchangeStatus.DONE,
  ExchangeStatus.CANCELLED,
  ExchangeStatus.DECLINED,
]

const wanted = ref<string>(props.request.status)
const reason = ref('')

const {running: saving, run: save} = useAsyncAction(async () => {
  await exchanges.correctStatus(props.request.id, {
    status: wanted.value as ExchangeStatusName,
    reason: reason.value.trim(),
  })
  emit('done')
  return true
}, {formatError: (e) => apiErrorMessage(e) ?? t('common.error')})

async function submit() {
  if (!wanted.value || !reason.value.trim()) return
  const ok = await save()
  if (!ok) emit('error', t('common.error'))
}
</script>

<template>
  <div class="flex flex-col gap-2 items-stretch" data-testid="exchange-correct-panel">
    <FieldLabel>{{ t('exchanges.correctTitle') }}</FieldLabel>
    <MutedText size="sm" tag="p">{{ t('exchanges.correctHint') }}</MutedText>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
      <div class="space-y-1">
        <FieldLabel hint>{{ t('exchanges.correctStatus') }}</FieldLabel>
        <SelectInput v-model="wanted" data-testid="exchange-correct-status">
          <option v-for="s in everyStatus" :key="s" :value="s">{{ t(`exchanges.status.${s}`) }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('exchanges.correctReason') }}</FieldLabel>
        <TextInput
            v-model="reason"
            data-testid="exchange-correct-reason"
            :placeholder="t('exchanges.correctReasonPlaceholder')"
        />
      </div>
    </div>
    <div class="flex gap-2 justify-end">
      <PrimaryButton
          :disabled="saving || !reason.trim()"
          data-testid="exchange-correct-submit"
          @click="submit"
      >
        {{ saving ? t('common.loading') : t('exchanges.correctSubmit') }}
      </PrimaryButton>
      <SecondaryButton data-testid="exchange-correct-cancel" @click="emit('cancel')">
        {{ t('common.cancel') }}
      </SecondaryButton>
    </div>
  </div>
</template>
