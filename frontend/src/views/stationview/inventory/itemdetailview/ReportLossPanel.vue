/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {inventory} from '@/api'
import {LossReportRequirement, type InventoryItem, type LossReportTerms} from '@/api/inventory'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Asking the body above the station to replace something that is gone.
 *
 * <p>Marking gear lost is a fact about where it is, and the association is never told. This is the separate
 * act that tells it: a request for a replacement, carrying what the reporting manager writes and, where the
 * association asks for one, a document. The member's own note travels with it as a reference and is not
 * repeated here, because it is theirs and not the manager's.
 */
const props = defineProps<{
  item: InventoryItem
}>()

const emit = defineEmits<{
  reported: []
}>()

const {t} = useI18n()

const terms = ref<LossReportTerms | null>(null)
const asking = ref(false)
const note = ref('')
const document = ref<File | null>(null)

const noteRequired = computed(() => terms.value?.requires !== LossReportRequirement.NOTHING)
const documentRequired = computed(() => terms.value?.requires === LossReportRequirement.DOCUMENT)
const canSend = computed(() =>
    (!noteRequired.value || note.value.trim().length > 0)
    && (!documentRequired.value || document.value != null))

onMounted(async () => {
  try {
    terms.value = await inventory.lossReportTerms(props.item.id)
  } catch {
    terms.value = null
  }
})

function pick(event: Event) {
  const input = event.target as HTMLInputElement
  document.value = input.files?.[0] ?? null
}

const {running, error, run: send} = useAsyncAction(async () => {
  await inventory.reportLoss(props.item.id, note.value.trim(), document.value)
  asking.value = false
  note.value = ''
  document.value = null
  emit('reported')
})
</script>

<template>
  <NeutralContainer v-if="terms?.reportable" class="space-y-3" data-testid="report-loss">
    <SectionHeader>{{ t('itemDetail.reportLossTitle') }}</SectionHeader>
    <MutedText size="sm">{{ t('itemDetail.reportLossHint') }}</MutedText>

    <SecondaryButton :icon="['fas', 'triangle-exclamation']" data-testid="report-loss-open" @click="asking = true">
      {{ t('itemDetail.reportLoss') }}
    </SecondaryButton>

    <Modal v-if="asking" model-value @update:model-value="(v) => { if (!v) asking = false }">
      <div class="space-y-4">
        <SectionHeader>{{ t('itemDetail.reportLoss') }}</SectionHeader>
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('itemDetail.reportLossNote') }}</FieldLabel>
          <TextAreaInput v-model="note" :placeholder="t('itemDetail.reportLossNotePlaceholder')"
                         data-testid="report-loss-note"/>
        </div>

        <div v-if="documentRequired" class="space-y-1">
          <FieldLabel>{{ t('itemDetail.reportLossDocument') }}</FieldLabel>
          <input type="file" data-testid="report-loss-document" class="text-sm" @change="pick"/>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton :disabled="running" @click="asking = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="running || !canSend" data-testid="report-loss-send" @click="send">
            {{ running ? t('common.loading') : t('common.send') }}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>
