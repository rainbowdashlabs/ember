/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import MailRecordTable from '@/components/mail/MailRecordTable.vue'
import MailProviderStanding from '@/components/mail/MailProviderStanding.vue'
import {useMailRecordTable} from '@/components/mail/useMailRecordTable'
import {showToast} from '@/util/toast'
import {describeFailure, type Failure} from '@/util/failure'
import {MailDeliveryStatus, type MailDashboard, type ProviderBlock, type RequeuedMails} from '@/api/mailProviders'

/**
 * What has become of the post.
 *
 * All of this was recorded from the start and none of it could be seen: how many mails wait, which
 * provider they wait at, and what the providers reported back about the ones they took. The same
 * screen serves the instance and a station because the question is the same one.
 */
const props = defineProps<{
  load: () => Promise<MailDashboard>
  /** Lifts a block by hand. Absent where nobody may. */
  lift?: (provider: string, domain: string) => Promise<void>
  /** Puts left-behind mails back in the queue, one or all of them. Absent where nobody may. */
  requeue?: (id?: number) => Promise<RequeuedMails>
  /** Whether the tables remember their columns per station: a station's page does, the instance's log does not. */
  perStation?: boolean
}>()

const {t} = useI18n()

const data = ref<MailDashboard | null>(null)
const loading = ref(true)
const loadFailure = ref<Failure | null>(null)
const actionFailure = ref<Failure | null>(null)

const statusFilter = ref('')

async function reload() {
  loading.value = true
  loadFailure.value = null
  try {
    data.value = await props.load()
  } catch (e) {
    loadFailure.value = describeFailure(e, t)
  } finally {
    loading.value = false
  }
}

/**
 * Fetching the dashboard again after something was done to it, which is not part of doing it.
 *
 * <p>Sharing one attempt meant a block that really was lifted, or a queue that really was sent again,
 * followed by a dashboard that failed to come back, read as the action having failed. Sending a queue
 * a second time on that advice is how one mail becomes two.
 */
async function catchUp() {
  await reload()
  if (loadFailure.value) {
    actionFailure.value = {...loadFailure.value, message: t('failure.staleAfterAction')}
    loadFailure.value = null
  }
}

const lifting = ref<string | null>(null)

async function doLift(block: ProviderBlock) {
  if (!props.lift) return
  lifting.value = `${block.provider}-${block.recipientDomain}`
  actionFailure.value = null
  try {
    await props.lift(block.provider, block.recipientDomain)
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    return
  } finally {
    lifting.value = null
  }
  await catchUp()
}

const requeueing = ref(false)

async function doRequeue(id?: number) {
  if (!props.requeue) return
  requeueing.value = true
  actionFailure.value = null
  try {
    const result = await props.requeue(id)
    showToast(t('mailDashboard.requeued', {count: result.requeued}), 'success')
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    return
  } finally {
    requeueing.value = false
  }
  await catchUp()
}

onMounted(reload)

/** The delivery states actually present, so the filter offers nothing that would match nothing. */
const deliveryStates = computed(() => {
  const seen = new Set((data.value?.recent ?? []).map(entry => entry.deliveryStatus).filter(Boolean))
  return Object.values(MailDeliveryStatus).filter(state => seen.has(state))
})

const recentTable = useMailRecordTable('mail-recent', () => (data.value?.recent ?? [])
    .filter(entry => !statusFilter.value || entry.deliveryStatus === statusFilter.value), props.perStation !== false)

const stuckTable = useMailRecordTable('mail-stuck', () => data.value?.stuckMails ?? [], props.perStation !== false)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <SectionHeader>{{ t('mailDashboard.title') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'arrows-rotate']" :disabled="loading" @click="reload">
        {{ t('common.refresh') }}
      </SecondaryButton>
    </div>
    <MutedText tag="p" size="sm">{{ t('mailDashboard.hint') }}</MutedText>

    <FailureAlert :failure="actionFailure"/>

    <Spinner v-if="loading" size="md"/>
    <FailureAlert v-else-if="loadFailure" :failure="loadFailure"/>

    <template v-else-if="data">
      <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div v-for="tile in [
          {key: 'pending', value: data.pending},
          {key: 'sending', value: data.sending},
          {key: 'sent', value: data.sent},
          {key: 'failed', value: data.failed},
          {key: 'stuck', value: data.stuck},
        ]" :key="tile.key" class="rounded-lg border border-(--border) p-3">
          <div class="text-2xl font-semibold">{{ tile.value }}</div>
          <div class="text-xs text-(--text-muted)">{{ t(`mailDashboard.${tile.key}`) }}</div>
        </div>
      </div>

      <Alert v-if="data.stuck > 0" variant="error">{{ t('mailDashboard.stuckWarning', {count: data.stuck}) }}</Alert>

      <template v-if="data.stuckMails.length > 0">
        <div class="flex items-center justify-between gap-2 flex-wrap">
          <SubHeader>{{ t('mailDashboard.stuckTitle') }}</SubHeader>
          <SecondaryButton
              v-if="props.requeue"
              :icon="['fas', 'arrows-rotate']"
              :disabled="requeueing"
              @click="doRequeue()"
          >
            {{ t('mailDashboard.requeueAll') }}
          </SecondaryButton>
        </div>
        <MutedText tag="p" size="sm">{{ t('mailDashboard.stuckHint') }}</MutedText>
        <MailRecordTable :table="stuckTable" test-id="mail-stuck-table">
          <template v-if="props.requeue" #actions="{row}">
            <SecondaryButton :disabled="requeueing" @click="doRequeue(row.id)">
              {{ t('mailDashboard.requeueOne') }}
            </SecondaryButton>
          </template>
        </MailRecordTable>
      </template>

      <MutedText v-if="data.oldestPendingAt" tag="p" size="sm">
        {{ t('mailDashboard.oldestPending', {when: new Date(data.oldestPendingAt).toLocaleString('de-DE')}) }}
      </MutedText>

      <SubHeader>{{ t('mailDashboard.providersTitle') }}</SubHeader>
      <EmptyHint v-if="data.providers.length === 0">{{ t('mailDashboard.noProviders') }}</EmptyHint>
      <MailProviderStanding v-for="standing in data.providers" :key="standing.position" :standing="standing"/>

      <template v-if="data.blocks.length > 0">
        <SubHeader>{{ t('mailDashboard.blocksTitle') }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t('mailDashboard.blocksHint') }}</MutedText>
        <div v-for="block in data.blocks" :key="`${block.provider}-${block.recipientDomain}`"
             class="rounded-lg border border-(--error) p-3 space-y-1">
          <div class="flex items-start justify-between gap-2 flex-wrap">
            <div class="text-sm font-medium">
              {{ t('mailDashboard.blockRow', {provider: block.provider, domain: block.recipientDomain}) }}
            </div>
            <SecondaryButton v-if="props.lift" :disabled="lifting !== null" @click="doLift(block)">
              {{ t('mailDashboard.liftBlock') }}
            </SecondaryButton>
          </div>
          <div class="text-xs text-(--text-muted)">
            {{ t('mailDashboard.blockUntil', {when: new Date(block.expiresAt).toLocaleString('de-DE')}) }}
          </div>
          <div v-if="block.reason" class="text-xs text-(--error) break-words">{{ block.reason }}</div>
        </div>
      </template>

      <SubHeader>{{ t('mailDashboard.recentTitle') }}</SubHeader>
      <div class="flex gap-2 flex-wrap">
        <TextInput
            v-model="recentTable.search"
            class="flex-1 min-w-56"
            :placeholder="t('mailDashboard.searchPlaceholder')"
            :aria-label="t('mailDashboard.searchPlaceholder')"
        />
        <SelectInput v-model="statusFilter" :aria-label="t('mailDashboard.deliveryFilter')">
          <option value="">{{ t('mailDashboard.allDeliveryStates') }}</option>
          <option v-for="state in deliveryStates" :key="state" :value="state">{{ t(`mailDashboard.delivery.${state}`) }}</option>
        </SelectInput>
        <TableColumnPicker :table="recentTable"/>
      </div>

      <MailRecordTable :table="recentTable" test-id="mail-recent-table"/>
    </template>
  </NeutralContainer>
</template>
