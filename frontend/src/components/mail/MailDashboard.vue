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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MailQueueRow from '@/components/mail/MailQueueRow.vue'
import MailProviderStanding from '@/components/mail/MailProviderStanding.vue'
import type {MailDashboard} from '@/api/mailProviders'

/**
 * What has become of the post.
 *
 * All of this was recorded from the start and none of it could be seen: how many mails wait, which
 * provider they wait at, and what the providers reported back about the ones they took. The same
 * screen serves the instance and a station because the question is the same one.
 */
const props = defineProps<{
  load: () => Promise<MailDashboard>
}>()

const {t} = useI18n()

const data = ref<MailDashboard | null>(null)
const loading = ref(true)
const error = ref('')

const search = ref('')
const statusFilter = ref('')

async function reload() {
  loading.value = true
  error.value = ''
  try {
    data.value = await props.load()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(reload)

/** The delivery states actually present, so the filter offers nothing that would match nothing. */
const deliveryStates = computed(() => {
  const seen = new Set((data.value?.recent ?? []).map(entry => entry.deliveryStatus).filter(Boolean))
  return [...seen].sort()
})

const visible = computed(() => {
  const term = search.value.trim().toLowerCase()
  return (data.value?.recent ?? []).filter(entry => {
    if (statusFilter.value && entry.deliveryStatus !== statusFilter.value) return false
    if (!term) return true
    return entry.recipient.toLowerCase().includes(term) || entry.subject.toLowerCase().includes(term)
  })
})
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

    <Spinner v-if="loading" size="md"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

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
      <MutedText v-if="data.oldestPendingAt" tag="p" size="sm">
        {{ t('mailDashboard.oldestPending', {when: new Date(data.oldestPendingAt).toLocaleString('de-DE')}) }}
      </MutedText>

      <SubHeader>{{ t('mailDashboard.providersTitle') }}</SubHeader>
      <EmptyHint v-if="data.providers.length === 0">{{ t('mailDashboard.noProviders') }}</EmptyHint>
      <MailProviderStanding v-for="standing in data.providers" :key="standing.position" :standing="standing"/>

      <SubHeader>{{ t('mailDashboard.recentTitle') }}</SubHeader>
      <div class="flex gap-2 flex-wrap">
        <TextInput
            v-model="search"
            class="flex-1 min-w-56"
            :placeholder="t('mailDashboard.searchPlaceholder')"
            :aria-label="t('mailDashboard.searchPlaceholder')"
        />
        <SelectInput v-model="statusFilter" :aria-label="t('mailDashboard.deliveryFilter')">
          <option value="">{{ t('mailDashboard.allDeliveryStates') }}</option>
          <option v-for="state in deliveryStates" :key="state" :value="state">{{ state }}</option>
        </SelectInput>
      </div>

      <EmptyHint v-if="visible.length === 0">{{ t('mailDashboard.noMails') }}</EmptyHint>
      <MailQueueRow v-for="entry in visible" :key="entry.id" :entry="entry"/>
    </template>
  </NeutralContainer>
</template>
