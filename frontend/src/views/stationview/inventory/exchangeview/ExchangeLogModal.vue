/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { AckKind, type ExchangeLogEntry } from '@/api/exchanges'
import { exchanges } from '@/api'
import { formatDate } from '@/util/format'

const { t } = useI18n()

const model = defineModel<boolean>({ required: true })

const props = defineProps<{
  exchangeId: number | null
}>()

const emit = defineEmits<{
  error: [msg: string]
}>()

const logEntries = ref<ExchangeLogEntry[]>([])
const logLoading = ref(false)

/**
 * How the step was acknowledged. A step the station asserted on behalf of an owner that does not use
 * Ember reads differently from one that owner confirmed itself, which is the point of recording it.
 */
function ackLabel(kind: string): string {
  return t(`exchanges.ack.${kind}`)
}

watch(model, async (open) => {
  if (open && props.exchangeId) {
    logLoading.value = true
    logEntries.value = []
    try {
      logEntries.value = await exchanges.getLogs(props.exchangeId)
    } catch {
      emit('error', t('common.error'))
    } finally {
      logLoading.value = false
    }
  }
})
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4">
      <SubHeader>{{ t('exchanges.logTitle') }}</SubHeader>
      <AsyncSection
          :empty="logEntries.length === 0"
          :empty-compact="true"
          :empty-message="t('exchanges.noLogs')"
          :loading="logLoading"
          spinner-size="md"
      >
        <div class="space-y-2">
          <NeutralContainer v-for="entry in logEntries" :key="entry.id" class="text-sm space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">{{ entry.stepLabel }}</span>
              <span v-if="entry.ackKind !== AckKind.CONFIRMED" class="text-xs text-(--text-muted)">
                {{ ackLabel(entry.ackKind) }}
              </span>
            </div>
            <div class="text-(--text-muted)">
              {{ entry.changedByName }} &mdash; {{ formatDate(entry.changedAt) }}
            </div>
            <div v-if="entry.note">{{ entry.note }}</div>
          </NeutralContainer>
        </div>
      </AsyncSection>
      <div class="flex justify-end">
        <SecondaryButton @click="model = false">{{ t('common.close') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
