/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {clusterContent} from '@/api'
import type {ClusterEvent} from '@/api/clusterContent'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const name = ref('')
const description = ref('')
const startTime = ref('')
const endTime = ref('')
const requiresRegistration = ref(false)
const busy = ref(false)

const {config: events, loading, error, runWith} = useConfigPanel<ClusterEvent[]>({
  initial: [],
  fetch: () => clusterContent.listEvents(),
})

const editable = hasClusterPermission(ClusterPermission.CLUSTER_EVENT_EDIT)

async function create() {
  if (!name.value.trim()) return
  await runWith(async () => {
    await clusterContent.createEvent({
      name: name.value.trim(),
      description: description.value,
      // The picker gives a local time; the backend wants an instant
      startTime: startTime.value ? new Date(startTime.value).toISOString() : null,
      endTime: endTime.value ? new Date(endTime.value).toISOString() : null,
      requiresRegistration: requiresRegistration.value,
    })
    name.value = ''
    description.value = ''
    startTime.value = ''
    endTime.value = ''
    return clusterContent.listEvents()
  }, {busy})
}

async function remove(eventId: number) {
  await runWith(async () => {
    await clusterContent.deleteEvent(eventId)
    return clusterContent.listEvents()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-events.subtitle')" :title="t('pages.cluster-events.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('clusterEvents.hint') }}</p>

      <NeutralContainer v-if="editable" class="space-y-4">
        <SectionHeader>{{ t('clusterEvents.createTitle') }}</SectionHeader>
        <div class="space-y-1">
          <FormLabel>{{ t('clusterEvents.nameLabel') }}</FormLabel>
          <TextInput v-model="name" :placeholder="t('clusterEvents.namePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FormLabel>{{ t('clusterEvents.descriptionLabel') }}</FormLabel>
          <TextAreaInput v-model="description" :rows="3"/>
        </div>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1">
            <FormLabel>{{ t('clusterEvents.startLabel') }}</FormLabel>
            <DateTimeInput v-model="startTime"/>
          </div>
          <div class="space-y-1">
            <FormLabel>{{ t('clusterEvents.endLabel') }}</FormLabel>
            <DateTimeInput v-model="endTime"/>
          </div>
        </div>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="requiresRegistration"/>
          <span>{{ t('clusterEvents.registrationLabel') }}</span>
        </label>
        <PrimaryButton :disabled="busy || !name.trim()" @click="create">{{ t('common.create') }}</PrimaryButton>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="events.length === 0">{{ t('clusterEvents.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <NeutralContainer v-for="event in events" :key="event.id" class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="font-medium">{{ event.name }}</p>
              <p class="text-sm text-(--text-muted)">{{ formatDateTime(event.startTime) }}</p>
            </div>
            <DeleteButton v-if="editable" :disabled="busy" @click="remove(event.id)"/>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
