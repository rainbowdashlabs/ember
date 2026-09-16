/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import QuestionValueInput from '@/components/input/QuestionValueInput.vue'
import {questionKindOf} from '@/util/questions'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import {calculatedAnswer, type FieldOrigin} from '@/util/profileFields'
import {isSection, isSpacer, spanClass} from './fieldLayout'

/**
 * The fields of a member, laid out the way the station arranged them: headings between them, and
 * the short ones beside each other rather than each on a row of its own.
 *
 * <p>The one place that decides this, so a field looks the same wherever it is filled in.
 */
/**
 * A field as this layout needs it: the station's own, or one its cluster added.
 *
 * The station is optional because a cluster's question belongs to no station: it is asked at every station
 * under the cluster, and the row it lives in names the cluster instead.
 */
export type LaidOutField = Omit<ProfileField, 'stationId'> & {
  stationId?: string
  origin?: FieldOrigin
  /**
   * Whether this reader may read the answer but not write it. Theirs rather than the question's: a
   * manager may write a date the member they are looking at only reads.
   */
  readonly?: boolean
  /** Set on a cluster field the cluster keeps to itself, which nobody at the station may write. */
  readonlyAtStation?: boolean
}

const props = defineProps<{
  fields: LaidOutField[]
  getValue: (field: LaidOutField) => string
  /** Whether the reader may write to fields the station marked read-only. */
  canEditReadonly?: boolean
}>()

const emit = defineEmits<{
  update: [field: LaidOutField, value: string]
}>()

const {t} = useI18n()

/**
 * Whether the input is shown but not offered.
 *
 * Two different reasons land here. The station can mark its own field read-only for everybody but its
 * managers, and a cluster can keep one of its questions to itself, which nobody at the station may
 * answer whatever they hold.
 */
function locked(field: LaidOutField): boolean {
  if (field.readonlyAtStation) return true
  if (worksItselfOut(field)) return true
  return !props.canEditReadonly && !!field.readonly
}

/**
 * The answer where it is worked out from another one, and null where it is given like any other.
 *
 * <p>Worked out here rather than by whoever draws this, so every form showing such a question shows
 * the same number, and the one thing the member profile did differently, showing what was once
 * typed into the question before it began working itself out, cannot happen again.
 *
 * <p>Only questions of the same owner are counted from: a station's and its cluster's are two sets
 * of questions with two sets of identifiers, and a number means one thing in each.
 */
function computedAnswer(field: LaidOutField): string | null {
  const owned = props.fields.filter(f => (f.origin ?? 'STATION') === (field.origin ?? 'STATION'))
  return calculatedAnswer(field, owned, id => {
    const source = owned.find(f => f.id === id)
    return source ? props.getValue(source) : ''
  })
}

function worksItselfOut(field: LaidOutField): boolean {
  return computedAnswer(field) !== null
}

/** What the input shows: the worked out answer where there is one, otherwise what was given. */
function valueOf(field: LaidOutField): string {
  return computedAnswer(field) ?? props.getValue(field)
}

/** The sentence the station wrote to say what the question is after, empty when it wrote none. */
function descriptionOf(field: LaidOutField): string {
  const described = parseFieldConfig(field.config).description
  return typeof described === 'string' ? described : ''
}
</script>

<template>
  <div class="grid grid-cols-6 gap-x-4 gap-y-3 items-start">
    <template v-for="field in props.fields" :key="`${field.origin ?? 'STATION'}-${field.id}`">
      <div v-if="isSection(field)" :class="spanClass(field)" class="pt-2 first:pt-0">
        <SubHeader class="text-sm">{{ field.name }}</SubHeader>
      </div>
      <div v-else-if="isSpacer(field)" :class="spanClass(field)" aria-hidden="true"></div>
      <div v-else :data-field="field.name" :class="spanClass(field)" class="space-y-1">
        <FieldLabel>
          {{ field.name }}
          <span v-if="field.required" class="text-error">*</span>
          <SecondaryBadge v-if="field.origin === 'CLUSTER'" class="ml-1">
            {{ t('memberEdit.fieldFromCluster') }}
          </SecondaryBadge>
          <MutedText v-if="worksItselfOut(field)" class="ml-1">({{ t('profile.calculatedHint') }})</MutedText>
          <MutedText v-else-if="locked(field)" class="ml-1">({{ t('profile.readonlyHint') }})</MutedText>
        </FieldLabel>
        <MutedText v-if="descriptionOf(field)" class="block text-xs">{{ descriptionOf(field) }}</MutedText>
        <QuestionValueInput
            :kind="questionKindOf(field.fieldType) ?? 'TEXT'"
            :model-value="valueOf(field)"
            :options="(parseFieldConfig(field.config).options as string[]) ?? []"
            :disabled="locked(field)"
            :required="!!field.required"
            @update:model-value="emit('update', field, $event)"
        />
      </div>
    </template>
  </div>
</template>
