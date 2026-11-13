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
import {eventGroups} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Makes a saved one-time appointment a part of an event group: an existing one, in a block of it,
 * or a new one the appointment joins once it is created.
 */
const props = defineProps<{
  eventId: number
  /** Called once the appointment joined a group, so the editor reloads what the group now decides. */
  onAdded?: () => void | Promise<void>
}>()

const {t} = useI18n()

const groups = ref<EventGroupSummary[]>([])
const groupId = ref('')
const sets = ref<EventGroupSet[]>([])
const setId = ref('')
const busy = ref(false)
const failure = ref<Failure | null>(null)

const open = computed(() => groups.value.filter(group => !group.cancelled))

onMounted(async () => {
  groups.value = await eventGroups.listEventGroups().catch(() => [])
})

watch(groupId, loadSets)

/**
 * Loads the blocks of the chosen group and preselects the first. An answer for a group that is no
 * longer the chosen one is dropped, so switching quickly never shows the blocks of another group.
 */
async function loadSets(id: string) {
  sets.value = []
  setId.value = ''
  failure.value = null
  if (!id) return
  try {
    const loaded = (await eventGroups.getEventGroup(Number(id))).sets
    if (groupId.value !== id) return
    sets.value = loaded
    setId.value = loaded[0] ? String(loaded[0].id) : ''
  } catch (e) {
    if (groupId.value === id) failure.value = describeFailure(e, t)
  }
}

async function add() {
  busy.value = true
  failure.value = null
  try {
    await eventGroups.addPart(Number(groupId.value), props.eventId, Number(setId.value))
    await props.onAdded?.()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="add-to-event-group">
    <SectionHeader>{{ t('eventGroups.addToGroup') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.addToGroupHint') }}</MutedText>
    <FailureAlert v-if="failure" :failure="failure"/>
    <div v-if="open.length > 0" class="grid gap-3 sm:grid-cols-2">
      <div>
        <FieldLabel>{{ t('eventGroups.chooseGroup') }}</FieldLabel>
        <SelectInput v-model="groupId" data-testid="add-to-event-group-group">
          <option value="">{{ t('eventGroups.chooseGroupNone') }}</option>
          <option v-for="group in open" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
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
      <PrimaryButton :icon="['fas', 'plus']" :disabled="busy || !groupId || !setId" data-testid="add-to-event-group-save"
                     @click="add">
        {{ t('eventGroups.addToGroupSave') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
