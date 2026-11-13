/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {CourseDetail, CourseSignUp} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {eventCourses} from '@/api'
import {useSession} from '@/composables/useSession'
import {asksForAll, choiceMeetsRules, evaluateChoice, isMet, missingOf} from '@/util/courseChoice'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDateTime} from '@/util/format'

/**
 * Signs somebody up for a course, or changes the parts they chose: every part as a box, grouped by
 * set with the set's minimum beside it. Saving stays off while a set is short, and the dialog says
 * which one and by how much. A part called off cannot be newly chosen; one already held stays. A
 * part that already took place stays as it is.
 *
 * <p>Whoever runs events signing somebody else up may break the rules: the shortfalls are then only
 * shown, and the course marks the person as short.
 */
const props = withDefaults(defineProps<{
  detail: CourseDetail
  entry: CourseSignUp
  /** Whether whoever runs events signs somebody up, which passes the rules and parts already past. */
  override?: boolean
}>(), {
  override: false,
})

const emit = defineEmits<{
  close: []
  saved: []
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()

const open = ref(true)
const busy = ref(false)
const failure = ref<Failure | null>(null)
const changing = computed(() =>
    props.entry.status === RegistrationStatus.PENDING || props.entry.status === RegistrationStatus.ACCEPTED)
const held = new Set(changing.value ? props.entry.parts : [])
const chosen = ref<Set<number>>(new Set(held))
const name = computed(() => props.entry.member.name ?? '')

const results = computed(() => evaluateChoice(props.detail.sets, props.detail.parts, chosen.value))
const meets = computed(() => props.override
    ? chosen.value.size > 0
    : choiceMeetsRules(props.detail.sets, props.detail.parts, chosen.value))

/**
 * Whether a part can be ticked or unticked: a part that took place stays as it is, and a part called
 * off can only stay where it was held. Whoever runs events passes the first rule.
 */
function selectable(eventId: number): boolean {
  const part = props.detail.parts.find(candidate => candidate.eventId === eventId)!
  if (part.tookPlace && !props.override) return false
  return held.has(eventId) || !part.calledOff
}

function toggle(eventId: number, on: boolean) {
  const next = new Set(chosen.value)
  if (on) next.add(eventId)
  else next.delete(eventId)
  chosen.value = next
}

function partsOf(setId: number) {
  return props.detail.parts.filter(part => part.setId === setId)
}

function ruleOf(setId: number): string {
  const set = props.detail.sets.find(candidate => candidate.id === setId)!
  if (asksForAll(set, props.detail.parts)) return t('courses.setAll')
  return t('courses.setRule', {minimum: set.minimum, count: partsOf(setId).length})
}

function close() {
  open.value = false
  emit('close')
}

async function save() {
  busy.value = true
  failure.value = null
  try {
    const parts = [...chosen.value]
    if (changing.value) await eventCourses.changeParts(props.detail.course.id, parts, props.entry.memberId)
    else await eventCourses.signUp(props.detail.course.id, parts, props.entry.memberId)
    open.value = false
    emit('saved')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="lg" @update:model-value="value => { if (!value) close() }">
    <div class="space-y-4" data-testid="course-sign-up-dialog">
      <SubHeader>{{ changing ? t('courses.dialogChangeTitle', {name}) : t('courses.dialogTitle', {name}) }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('courses.dialogHint', {name}) }}</MutedText>
      <FailureAlert v-if="failure" :failure="failure"/>
      <fieldset v-for="set in detail.sets" :key="set.id" class="space-y-1">
        <legend class="font-medium">{{ set.name }} <MutedText tag="span" size="sm">({{ ruleOf(set.id) }})</MutedText></legend>
        <FieldLabel v-for="part in partsOf(set.id)" :key="part.eventId" inline>
          <CheckboxInput
              :model-value="chosen.has(part.eventId)"
              :disabled="!selectable(part.eventId)"
              :data-testid="`course-part-${part.eventId}`"
              @update:model-value="value => toggle(part.eventId, value === true)"
          />
          <span :class="{'line-through': part.calledOff}">{{ part.name }}</span>
          <MutedText tag="span" size="sm">{{ formatDateTime(part.startTime, stationTimezone) }}</MutedText>
        </FieldLabel>
      </fieldset>
      <ul class="text-sm space-y-1" :class="override ? 'text-(--text-muted)' : 'text-error'" data-testid="course-shortfalls">
        <li v-if="chosen.size === 0">{{ t('courses.chooseSomething') }}</li>
        <template v-for="result in results" :key="result.setId">
          <li v-if="!isMet(result)">{{ t('courses.setShort', {name: result.name, missing: missingOf(result)}) }}</li>
        </template>
      </ul>
      <ButtonRow pair align="end">
        <SecondaryButton data-cancel :disabled="busy" @click="close">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="busy || !meets" data-testid="course-sign-up-save" @click="save">
          {{ changing ? t('courses.changeParts') : t('courses.signUp') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
