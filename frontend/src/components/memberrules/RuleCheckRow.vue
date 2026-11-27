/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, inject, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FieldOrigin, RuleSubject, type RuleNode, type RuleOperator} from '@/api/generated/schema'
import {todayIsoDate} from '@/util/format'
import type {RuleCatalog} from './ruleCatalog'
import RuleValueInput from './RuleValueInput.vue'
import {
  subjectReadsMemberData,
  LOST_CHECKS,
  complete,
  countsFromReference,
  fieldOf,
  fieldsFor,
  needOf,
  operatorsFor,
  readsField,
  readsMemberData,
  withField,
  withOperator,
  withSubject,
} from './ruleDraft'

/**
 * One check of a rule as a row of pickers: what it looks at, which field, group or tag, how it compares, what
 * with, and from which day where it counts from one. A field that is gone is named as such, since such a
 * check makes the whole rule match nobody. A check that is not complete yet shows that. A reader who may not read
 * members sees a check on member data only as such, since the fields it reads are not theirs to list.
 */
const props = defineProps<{
  check: RuleNode
  catalog: RuleCatalog
}>()

const emit = defineEmits<{
  change: [check: RuleNode]
  remove: []
}>()

const {t} = useI18n()

const subjects = computed(() => Object.values(RuleSubject).filter(subject => {
  if (subject === props.check.subject) return true
  if (subjectReadsMemberData(subject) && !props.catalog.readsMembers) return false
  if (subject === RuleSubject.CLUSTER_FIELD) {
    return props.catalog.fields.some(field => field.origin === FieldOrigin.CLUSTER)
  }
  return true
}))

const subject = computed(() => props.check.subject ?? RuleSubject.GROUP)
const field = computed(() => fieldOf(props.check, props.catalog.fields))
const fieldChoices = computed(() => fieldsFor(subject.value, props.catalog.fields, props.catalog.options))
const fieldKey = computed(() => field.value ? `${field.value.origin}:${field.value.id}` : null)
const hidden = computed(() => !props.catalog.readsMembers && readsMemberData(props.check))
const lost = inject(LOST_CHECKS, ref(new Set<RuleNode>()))
const fieldGone = computed(() => lost.value.has(props.check))
const incomplete = computed(() => !hidden.value && !fieldGone.value
    && !complete(props.check, props.catalog.options, props.catalog.fields))
const fieldUnreadable = computed(() => !hidden.value && !fieldGone.value && readsField(subject.value)
    && (props.check.fieldId !== null || props.check.clusterFieldId !== null) && !field.value)

const operators = computed(() => operatorsFor(props.catalog.options, subject.value, field.value?.fieldType))
const need = computed(() => needOf(props.check, props.catalog.options, props.catalog.fields))
const targets = computed(() => subject.value === RuleSubject.GROUP ? props.catalog.groups
    : subject.value === RuleSubject.TAG ? props.catalog.tags : [])
const targetId = computed(() => subject.value === RuleSubject.GROUP ? props.check.groupId : props.check.tagId)
const referenced = computed(() => countsFromReference(props.check))

function pickSubject(value: string | number | null | undefined) {
  emit('change', withSubject(props.check, value as RuleSubject, props.catalog.options))
}

function pickField(value: string | number | null | undefined) {
  const picked = fieldChoices.value.find(choice => `${choice.origin}:${choice.id}` === value)
  if (picked) emit('change', withField(props.check, picked, props.catalog.options))
}

function pickTarget(value: string | number | null | undefined) {
  const id = value === null || value === undefined || value === '' ? null : Number(value)
  emit('change', subject.value === RuleSubject.GROUP
      ? {...props.check, groupId: id}
      : {...props.check, tagId: id})
}

function pickOperator(value: string | number | null | undefined) {
  emit('change', withOperator(props.check, value as RuleOperator, props.catalog.options, props.catalog.fields))
}

function pickReference(fixed: string | number | null | undefined) {
  emit('change', {...props.check, referenceDate: fixed === 'today' ? null : props.check.referenceDate ?? todayIsoDate()})
}
</script>

<template>
  <div class="flex flex-col gap-1">
    <MutedText v-if="hidden" tag="p">
      {{ t(`memberRules.subjects.${subject}`) }}: {{ t('memberRules.readsMemberData') }}
    </MutedText>
    <div v-else class="flex flex-wrap items-center gap-2">
      <SelectInput :model-value="subject" :aria-label="t('memberRules.subject')" @update:model-value="pickSubject">
        <option v-for="option in subjects" :key="option" :value="option">
          {{ t(`memberRules.subjects.${option}`) }}
        </option>
      </SelectInput>
      <SelectInput v-if="readsField(subject)" :model-value="fieldKey" :aria-label="t('memberRules.pickField')"
                   @update:model-value="pickField">
        <option :value="null" disabled>{{ t('memberRules.pickField') }}</option>
        <option v-for="choice in fieldChoices" :key="`${choice.origin}:${choice.id}`"
                :value="`${choice.origin}:${choice.id}`">
          {{ choice.name }}
        </option>
      </SelectInput>
      <SelectInput v-if="subject === RuleSubject.GROUP || subject === RuleSubject.TAG"
                   :model-value="targetId" :aria-label="t(subject === RuleSubject.GROUP ? 'memberRules.pickGroup' : 'memberRules.pickTag')"
                   @update:model-value="pickTarget">
        <option :value="null" disabled>
          {{ t(subject === RuleSubject.GROUP ? 'memberRules.pickGroup' : 'memberRules.pickTag') }}
        </option>
        <option v-for="target in targets" :key="target.id" :value="target.id">{{ target.name }}</option>
      </SelectInput>
      <SelectInput v-if="operators.length > 0" :model-value="check.operator" :aria-label="t('memberRules.operator')"
                   @update:model-value="pickOperator">
        <option v-for="option in operators" :key="option.operator" :value="option.operator">
          {{ t(`memberRules.operators.${option.operator}`) }}
        </option>
      </SelectInput>
      <RuleValueInput v-if="need" :need="need" :value="check.value" :answers="field?.options ?? []"
                      @change="value => emit('change', {...check, value})"/>
      <template v-if="referenced">
        <span>{{ t('memberRules.reference') }}</span>
        <SelectInput :model-value="check.referenceDate === null ? 'today' : 'fixed'"
                     :aria-label="t('memberRules.reference')" @update:model-value="pickReference">
          <option value="today">{{ t('memberRules.today') }}</option>
          <option value="fixed">{{ t('memberRules.fixedDay') }}</option>
        </SelectInput>
        <DateInput v-if="check.referenceDate !== null" :model-value="check.referenceDate"
                   :aria-label="t('memberRules.referenceDay')"
                   @update:model-value="day => emit('change', {...check, referenceDate: day || null})"/>
      </template>
      <IconButton :icon="['fas', 'trash']" :label="t('memberRules.removeNode')" @click="emit('remove')"/>
    </div>
    <MutedText v-if="fieldGone">{{ t('memberRules.fieldGone') }}</MutedText>
    <MutedText v-else-if="fieldUnreadable">{{ t('memberRules.fieldUnreadable') }}</MutedText>
    <MutedText v-else-if="incomplete" class="text-error">{{ t('memberRules.incomplete') }}</MutedText>
  </div>
</template>
