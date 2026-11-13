/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {CourseDetail, EventSummary} from '@/api/generated/schema'
import {EventType} from '@/api/generated/schema'
import {eventCourses, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDateTime, localInputToInstant} from '@/util/format'

/**
 * The parts of a course: each with the set it belongs to, which can be changed, and a way to take it
 * out of the course. New parts are an existing one-time appointment, or a new appointment created
 * straight into the course, which announces nothing on its own.
 */
const props = defineProps<{
  detail: CourseDetail
  /** Called after the parts changed, so the editor reloads the course. */
  onChanged?: () => void | Promise<void>
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()

const candidates = ref<EventSummary[]>([])
const failure = ref<Failure | null>(null)
const busy = ref(false)
const firstSet = computed(() => props.detail.sets[0]?.id ?? 0)
const existingId = ref<number | null>(null)
const existingSet = ref<number>(firstSet.value)
const newName = ref('')
const newStart = ref('')
const newEnd = ref('')
const newSet = ref<number>(firstSet.value)
const removing = ref<number | null>(null)
const removeOpen = ref(false)

watch(() => props.detail.sets, sets => {
  const ids = sets.map(set => set.id)
  if (!ids.includes(existingSet.value)) existingSet.value = firstSet.value
  if (!ids.includes(newSet.value)) newSet.value = firstSet.value
})

/** One-time appointments still to come that belong to no course. */
async function loadCandidates() {
  const now = Date.now()
  candidates.value = (await events.listEvents())
      .filter(event => event.eventType === EventType.ONE_TIME && event.course == null)
      .filter(event => new Date(event.startTime).getTime() > now)
}

onMounted(() => {
  loadCandidates().catch(e => { failure.value = describeFailure(e, t) })
})

async function act(work: () => Promise<unknown>) {
  busy.value = true
  failure.value = null
  try {
    await work()
    await props.onChanged?.()
    await loadCandidates()
  } catch (e) {
    failure.value = describeFailure(e, t)
    try {
      await props.onChanged?.()
    } catch {
      return
    }
  } finally {
    busy.value = false
  }
}

function move(eventId: number, setId: number) {
  return act(() => eventCourses.movePart(props.detail.course.id, eventId, setId))
}

function askRemove(eventId: number) {
  removing.value = eventId
  removeOpen.value = true
}

async function confirmRemove() {
  const eventId = removing.value
  removeOpen.value = false
  if (eventId != null) await act(() => eventCourses.removePart(props.detail.course.id, eventId))
}

function addExisting() {
  const eventId = existingId.value
  if (eventId == null) return
  return act(async () => {
    await eventCourses.addPart(props.detail.course.id, eventId, existingSet.value)
    existingId.value = null
  })
}

function createNew() {
  return act(async () => {
    await eventCourses.createPart(props.detail.course.id, {
      setId: newSet.value,
      name: newName.value,
      description: null,
      startTime: localInputToInstant(newStart.value),
      endTime: localInputToInstant(newEnd.value),
    })
    newName.value = ''
    newStart.value = ''
    newEnd.value = ''
  })
}
</script>

<template>
  <div class="space-y-4" data-testid="course-parts-editor">
    <FailureAlert v-if="failure" :failure="failure"/>
    <MutedText v-if="detail.parts.length === 0" tag="p" size="sm">{{ t('courses.noParts') }}</MutedText>
    <ul class="divide-y divide-(--border)">
      <li v-for="part in detail.parts" :key="part.eventId" class="flex flex-wrap items-center justify-between gap-2 py-2">
        <div class="min-w-0">
          <p class="font-medium truncate">{{ part.name }}</p>
          <MutedText tag="p" size="sm">{{ formatDateTime(part.startTime, stationTimezone) }}</MutedText>
        </div>
        <div class="flex items-center gap-2">
          <SelectInput
              :model-value="part.setId"
              :aria-label="t('courses.partSet')"
              :disabled="busy"
              @update:model-value="value => move(part.eventId, Number(value))"
          >
            <option v-for="set in detail.sets" :key="set.id" :value="set.id">{{ set.name }}</option>
          </SelectInput>
          <IconButton :icon="['fas', 'link-slash']" :label="t('courses.removePart')" :disabled="busy" @click="askRemove(part.eventId)"/>
        </div>
      </li>
    </ul>

    <div class="space-y-2">
      <SubHeader>{{ t('courses.addExisting') }}</SubHeader>
      <div class="flex flex-wrap items-end gap-2">
        <SelectInput v-model="existingId" :aria-label="t('courses.chooseEvent')" class="flex-1 min-w-48" data-testid="course-existing-event">
          <option :value="null">{{ t('courses.chooseEvent') }}</option>
          <option v-for="event in candidates" :key="event.id" :value="event.id">
            {{ event.name }} ({{ formatDateTime(event.startTime, stationTimezone) }})
          </option>
        </SelectInput>
        <SelectInput v-model="existingSet" :aria-label="t('courses.partSet')">
          <option v-for="set in detail.sets" :key="set.id" :value="set.id">{{ set.name }}</option>
        </SelectInput>
        <PrimaryButton :icon="['fas', 'plus']" :disabled="busy || existingId == null" @click="addExisting">
          {{ t('courses.addExisting') }}
        </PrimaryButton>
      </div>
    </div>

    <div class="space-y-2">
      <SubHeader>{{ t('courses.createPart') }}</SubHeader>
      <FieldLabel for="course-part-name">{{ t('courses.partName') }}</FieldLabel>
      <TextInput id="course-part-name" v-model="newName"/>
      <div class="grid gap-2 sm:grid-cols-3">
        <div>
          <FieldLabel for="course-part-start">{{ t('courses.partStart') }}</FieldLabel>
          <DateTimeInput id="course-part-start" v-model="newStart"/>
        </div>
        <div>
          <FieldLabel for="course-part-end">{{ t('courses.partEnd') }}</FieldLabel>
          <DateTimeInput id="course-part-end" v-model="newEnd"/>
        </div>
        <div>
          <FieldLabel for="course-part-set">{{ t('courses.partSet') }}</FieldLabel>
          <SelectInput id="course-part-set" v-model="newSet">
            <option v-for="set in detail.sets" :key="set.id" :value="set.id">{{ set.name }}</option>
          </SelectInput>
        </div>
      </div>
      <PrimaryButton
          :icon="['fas', 'plus']"
          :disabled="busy || newName.trim() === '' || !newStart || !newEnd"
          data-testid="course-create-part"
          @click="createNew"
      >
        {{ t('courses.createPart') }}
      </PrimaryButton>
    </div>
    <ConfirmDeleteModal
        v-model="removeOpen"
        :title="t('courses.removePart')"
        :message="t('courses.removePartText')"
        :confirm-label="t('courses.removePart')"
        @confirm="confirmRemove"
    />
  </div>
</template>
