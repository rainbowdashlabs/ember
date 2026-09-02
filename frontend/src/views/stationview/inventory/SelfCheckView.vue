/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SelfCheckHeader from './selfcheckview/SelfCheckHeader.vue'
import SelfCheckSubmitBar from './selfcheckview/SelfCheckSubmitBar.vue'
import SelfCheckSection from './selfcheckview/SelfCheckSection.vue'
import ReportLostModal from '../profile/inventoryview/ReportLostModal.vue'
import ExchangeModal from '../profile/inventoryview/ExchangeModal.vue'
import {exchanges, inventory, selfChecks} from '@/api'
import type {InventorySize, RequiredInventoryItem} from '@/api/inventory'
import {SelfCheckState, type SelfCheckAnswerName, type SelfCheckResponse} from '@/api/selfChecks'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {ExchangeCause, useSelfCheck, type ExchangeCauseName, type SelfCheckEntry} from '@/composables/useSelfCheck'

const {t} = useI18n()
const route = useRoute()

const taskId = computed(() => Number(route.params.id))

const {config: task, loading, error, reload} = useConfigPanel<SelfCheckResponse | null>({
  initial: null,
  fetch: async () => {
    const loaded = await selfChecks.readTask(taskId.value)
    check.adoptSaved(loaded.rows)
    return loaded
  },
})

const check = useSelfCheck(task)

const readOnly = computed(() => task.value?.task.state !== SelfCheckState.OPEN)
const outstandingRows = computed(() => (task.value?.rows ?? []).filter(row => row.state === 'OUTSTANDING'))

const saved = ref('')

function sizeLabel(req: RequiredInventoryItem, sizeId?: number | null): string {
  if (sizeId == null) return ''
  return req.sizes.find(size => size.id === sizeId)?.label ?? ''
}

function setAnswer(key: string, answer: SelfCheckAnswerName) {
  saved.value = ''
  check.setDraft(key, {answer})
}

function setNote(key: string, note: string) {
  saved.value = ''
  check.setDraft(key, {note})
}

function setTypedInternalId(key: string, typedInternalId: string) {
  saved.value = ''
  check.setDraft(key, {typedInternalId})
}

function setSizeId(key: string, sizeId: string) {
  saved.value = ''
  check.setDraft(key, {sizeId})
}

/**
 * Puts away what has been said so far, so a member can go and put the boots on and come back.
 *
 * <p>Nothing is handed in by saving: the task stays open and the answers stay the member's to change.
 */
const {running: saving, error: saveError, run: save} = useAsyncAction(async () => {
  if (check.pending.value.length === 0) return
  await selfChecks.saveAnswers(taskId.value, check.pending.value)
  saved.value = t('selfCheck.saved')
})

const {running: submitting, error: submitError, run: submit} = useAsyncAction(async () => {
  if (check.pending.value.length > 0) await selfChecks.saveAnswers(taskId.value, check.pending.value)
  await selfChecks.submitTask(taskId.value)
  await reload()
})

const lostEntry = ref<SelfCheckEntry | null>(null)
const showLost = ref(false)
const lostNote = ref('')
const lostNoteRequired = ref(false)

/**
 * Saying a piece cannot be found, which is the same act as from the member's own gear page: it is
 * not a request and nobody answers it. The task only records that it happened here.
 */
async function openLost(entry: SelfCheckEntry) {
  if (entry.type !== 'piece') return
  lostEntry.value = entry
  lostNote.value = ''
  clearLostError()
  try {
    lostNoteRequired.value = (await inventory.getSettings()).lossNoteRequired
  } catch {
    lostNoteRequired.value = false
  }
  showLost.value = true
}

const {running: submittingLost, error: lostError, run: submitLost, clearError: clearLostError} = useAsyncAction(
    async () => {
      const entry = lostEntry.value
      if (entry?.type !== 'piece') return
      await inventory.markLost(entry.item.id, {
        note: lostNote.value.trim() || undefined,
        selfCheckId: taskId.value,
      })
      showLost.value = false
      await reload()
    },
)

const exchangeEntry = ref<SelfCheckEntry | null>(null)
const showExchange = ref(false)
const exchangeReason = ref('')
const exchangeNewSizeId = ref('')
const exchangeSizes = ref<InventorySize[]>([])
const exchangeCause = ref<ExchangeCauseName>(ExchangeCause.DOES_NOT_FIT)

/** Whether another size is the point of the exchange, which is what makes naming one unavoidable. */
const wantsAnotherSize = computed(() => exchangeCause.value === ExchangeCause.DOES_NOT_FIT)

/** What the exchange says it is for, which is the half of the reason nobody has to type. */
const causeText = computed(() => t(`selfCheck.exchangeCause.${exchangeCause.value}`))

/**
 * Asking for a swap, which is on its way rather than waiting: the exchange lands on the station's
 * list at once and the task only records that it was raised here.
 *
 * <p>A broken piece starts on the size it already is, because the replacement usually is that size.
 * A piece that no longer fits starts on nothing, because the whole point is that the size changes.
 */
async function openExchange(entry: SelfCheckEntry, cause: ExchangeCauseName) {
  if (entry.type !== 'piece') return
  exchangeEntry.value = entry
  exchangeCause.value = cause
  exchangeReason.value = ''
  exchangeNewSizeId.value =
      cause === ExchangeCause.BROKEN && entry.item.sizeId != null ? String(entry.item.sizeId) : ''
  clearExchangeError()
  exchangeSizes.value = entry.req.sizes
  showExchange.value = true
}

const {
  running: submittingExchange,
  error: exchangeError,
  run: submitExchange,
  clearError: clearExchangeError,
} = useAsyncAction(async () => {
  const entry = exchangeEntry.value
  if (entry?.type !== 'piece') return
  const ownWords = exchangeReason.value.trim()
  await exchanges.createExchange({
    memberId: task.value?.task.memberId,
    itemId: entry.item.id,
    inventoryId: entry.req.inventoryId,
    oldSizeId: entry.item.sizeId ?? undefined,
    newSizeId: exchangeNewSizeId.value ? Number(exchangeNewSizeId.value) : undefined,
    reason: ownWords ? `${causeText.value} - ${ownWords}` : causeText.value,
    selfCheckId: taskId.value,
  })
  showExchange.value = false
  await reload()
})

const anyError = computed(() => error.value || saveError.value || submitError.value || '')

const lostPiece = computed(() =>
    lostEntry.value?.type === 'piece'
        ? {
          inventoryName: lostEntry.value.req.inventoryName,
          name: lostEntry.value.item.name,
          sizeName: sizeLabel(lostEntry.value.req, lostEntry.value.item.sizeId) || null,
        }
        : null,
)

const exchangePiece = computed(() =>
    exchangeEntry.value?.type === 'piece'
        ? {
          inventoryName: exchangeEntry.value.req.inventoryName,
          name: exchangeEntry.value.item.name,
          sizeId: exchangeEntry.value.item.sizeId,
          sizeName: sizeLabel(exchangeEntry.value.req, exchangeEntry.value.item.sizeId) || null,
        }
        : null,
)
</script>

<template>
  <ViewContent :title="t('pages.inventory-self-check.title')" :subtitle="t('pages.inventory-self-check.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="anyError" variant="error">{{ anyError }}</Alert>

      <template v-if="!loading && task">
        <SelfCheckHeader :task="task.task" :outstanding-count="outstandingRows.length"/>

        <SelfCheckSection
            v-for="req in task.required"
            :key="req.inventoryId"
            :req="req"
            :entries="check.entriesFor(req.inventoryId)"
            :draft-of="check.draftOf"
            :size-label="sizeLabel"
            :raised-for="check.raisedFor.value"
            :refused-for="check.refusedFor.value"
            :read-only="readOnly"
            @set-answer="setAnswer"
            @set-note="setNote"
            @set-typed-internal-id="setTypedInternalId"
            @set-size-id="setSizeId"
            @report-lost="openLost"
            @request-exchange="openExchange"
        />

        <Alert v-if="saved" variant="success">{{ saved }}</Alert>

        <SelfCheckSubmitBar
            v-if="!readOnly"
            :open-count="check.unanswered.value.length"
            :saving="saving"
            :submitting="submitting"
            @save="save"
            @submit="submit"
        />
      </template>
    </div>

    <ReportLostModal
        v-model="showLost"
        v-model:note="lostNote"
        :item="lostPiece"
        :note-required="lostNoteRequired"
        :submitting="submittingLost"
        :error="lostError"
        @cancel="showLost = false"
        @submit="submitLost"
    />

    <ExchangeModal
        v-model="showExchange"
        v-model:reason="exchangeReason"
        v-model:new-size-id="exchangeNewSizeId"
        :item="exchangePiece"
        :sizes="exchangeSizes"
        :cause="causeText"
        :size-required="wantsAnotherSize"
        :submitting="submittingExchange"
        :error="exchangeError"
        @cancel="showExchange = false"
        @submit="submitExchange"
    />
  </ViewContent>
</template>
