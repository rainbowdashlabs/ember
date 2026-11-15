/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {EventGroupSet, EventGroupSummary} from '@/api/generated/schema'
import {eventGroups as eventGroupsApi} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Makes a saved one-time appointment a part of an event group: an existing one, in a set of it,
 * or a new one the appointment joins once it is created.
 */
const props = defineProps<{
  eventId: number
  /** Called once the appointment joined an event group, so the editor reloads what the event group now decides. */
  onAdded?: () => void | Promise<void>
}>()

const {t} = useI18n()

const eventGroups = ref<EventGroupSummary[]>([])
const eventGroupId = ref('')
const sets = ref<EventGroupSet[]>([])
const setId = ref('')

const openEventGroups = computed(() => eventGroups.value.filter(eventGroup => !eventGroup.cancelled))

onMounted(async () => {
  eventGroups.value = await eventGroupsApi.listEventGroups().catch(() => [])
})

/**
 * Loads the sets of the chosen event group and preselects the first. An answer for an event group that
 * is no longer the chosen one is dropped, so switching quickly never shows the sets of another one.
 */
const {failure: setsFailure, reload: loadSets} = useAsyncLoader(async isCurrent => {
  sets.value = []
  setId.value = ''
  if (!eventGroupId.value) return
  const loaded = (await eventGroupsApi.getEventGroup(Number(eventGroupId.value))).sets
  if (!isCurrent()) return
  sets.value = loaded
  setId.value = loaded[0] ? String(loaded[0].id) : ''
}, {autoLoad: false})

const {running: busy, failure: addFailure, run: add, clearError} = useAsyncAction(async () => {
  await eventGroupsApi.addPart(Number(eventGroupId.value), props.eventId, Number(setId.value))
  await props.onAdded?.()
})
const failure = computed(() => addFailure.value ?? setsFailure.value)

watch(eventGroupId, () => {
  clearError()
  void loadSets()
})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="add-to-event-group">
    <SectionHeader>{{ t('eventGroups.addToGroup') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.addToGroupHint') }}</MutedText>
    <FailureAlert v-if="failure" :failure="failure"/>
    <div v-if="openEventGroups.length > 0" class="grid gap-3 sm:grid-cols-2">
      <div>
        <FieldLabel>{{ t('eventGroups.chooseGroup') }}</FieldLabel>
        <SelectInput v-model="eventGroupId" data-testid="add-to-event-group-group">
          <option value="">{{ t('eventGroups.chooseGroupNone') }}</option>
          <option v-for="eventGroup in openEventGroups" :key="eventGroup.id" :value="String(eventGroup.id)">{{ eventGroup.details.name }}</option>
        </SelectInput>
      </div>
      <div v-if="sets.length > 0">
        <FieldLabel>{{ t('eventGroups.chooseSet') }}</FieldLabel>
        <SelectInput v-model="setId" data-testid="add-to-event-group-set">
          <option v-for="set in sets" :key="set.id" :value="String(set.id)">{{ set.name }}</option>
        </SelectInput>
      </div>
    </div>
    <ButtonRow align="end">
      <SecondaryButton :icon="['fas', 'layer-group']" :to="{name: 'event-group-new', query: {appointment: eventId}}"
                       data-testid="add-to-new-event-group">
        {{ t('eventGroups.addToNewGroup') }}
      </SecondaryButton>
      <PrimaryButton :icon="['fas', 'plus']" :disabled="busy || !eventGroupId || !setId" data-testid="add-to-event-group-save"
                     @click="add">
        {{ t('eventGroups.addToGroupSave') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
