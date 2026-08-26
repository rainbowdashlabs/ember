/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {ItemCustody} from '@/api/inventory'
import {StepActor, StepSubject, type MovementFlowStep, type StepRequest} from '@/api/movements'

const {t} = useI18n()

const props = defineProps<{
  step: MovementFlowStep
  editable: boolean
  /** Whether there is a step above this one to swap with. */
  canMoveUp: boolean
  /** Whether there is one below. */
  canMoveDown: boolean
}>()

const emit = defineEmits<{
  archive: []
  save: [step: StepRequest]
  move: [direction: -1 | 1]
}>()

const editing = ref(false)
const label = ref(props.step.label)
const actor = ref(props.step.actor)
const subject = ref(props.step.subject)
const custodyAfter = ref(props.step.custodyAfter)
const picksItem = ref(props.step.picksItem)

/** Opening the form reads the step again, so a cancelled edit leaves nothing behind. */
function open() {
  label.value = props.step.label
  actor.value = props.step.actor
  subject.value = props.step.subject
  custodyAfter.value = props.step.custodyAfter
  picksItem.value = props.step.picksItem
  editing.value = true
}

function save() {
  if (!label.value.trim()) return
  emit('save', {
    label: label.value.trim(),
    actor: actor.value,
    subject: subject.value,
    custodyAfter: custodyAfter.value,
    picksItem: picksItem.value,
  })
  editing.value = false
}
</script>

<template>
  <div class="py-1" :class="props.step.archived ? 'opacity-50' : ''">
    <div v-if="!editing" class="flex items-start justify-between gap-2">
      <div class="text-sm">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="text-(--text-muted)">{{ props.step.position + 1 }}.</span>
          <span class="font-medium">{{ props.step.label }}</span>
          <SecondaryBadge>{{ t(`movements.actor.${props.step.actor}`) }}</SecondaryBadge>
          <InfoBadge v-if="props.step.picksItem">{{ t('flows.picksItem') }}</InfoBadge>
          <SecondaryBadge v-if="props.step.archived">{{ t('flows.archived') }}</SecondaryBadge>
        </div>
        <div class="text-xs text-(--text-muted)">
          {{ t(`movements.subject.${props.step.subject}`) }} ·
          {{ t(`itemDetail.custodyValues.${props.step.custodyAfter}`) }}
        </div>
      </div>
      <div v-if="props.editable && !props.step.archived" class="flex items-center gap-1 shrink-0">
        <MutedIconButton
            :disabled="!props.canMoveUp"
            :icon="['fas', 'arrow-up']"
            :label="t('flows.moveUp')"
            data-testid="flow-step-up"
            @click="emit('move', -1)"
        />
        <MutedIconButton
            :disabled="!props.canMoveDown"
            :icon="['fas', 'arrow-down']"
            :label="t('flows.moveDown')"
            data-testid="flow-step-down"
            @click="emit('move', 1)"
        />
        <MutedIconButton
            :icon="['fas', 'pen']"
            :label="t('flows.editStep')"
            data-testid="flow-step-edit"
            @click="open"
        />
        <MutedIconButton
            :icon="['fas', 'xmark']"
            :label="t('flows.archiveStep')"
            hover="error"
            @click="emit('archive')"
        />
      </div>
    </div>

    <div v-else class="space-y-2 rounded-md border border-(--border) p-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('flows.stepLabel') }}</FieldLabel>
        <TextInput v-model="label" data-testid="flow-step-label"/>
      </div>
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('flows.actor') }}</FieldLabel>
          <SelectInput v-model="actor" class="w-full">
            <option v-for="value in [StepActor.MEMBER, StepActor.STATION, StepActor.OWNER]" :key="value" :value="value">
              {{ t(`movements.actor.${value}`) }}
            </option>
          </SelectInput>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('flows.subject') }}</FieldLabel>
          <SelectInput v-model="subject" class="w-full">
            <option v-for="value in [StepSubject.OUTGOING, StepSubject.INCOMING]" :key="value" :value="value">
              {{ t(`movements.subject.${value}`) }}
            </option>
          </SelectInput>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('flows.custodyAfter') }}</FieldLabel>
          <SelectInput v-model="custodyAfter" class="w-full">
            <option
                v-for="value in [ItemCustody.WITH_OWNER, ItemCustody.AT_STATION,
                                 ItemCustody.WITH_MEMBER, ItemCustody.IN_TRANSIT]"
                :key="value"
                :value="value"
            >
              {{ t(`itemDetail.custodyValues.${value}`) }}
            </option>
          </SelectInput>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <ToggleInput v-model="picksItem" :aria-label="t('flows.picksItem')"/>
        <span class="text-sm">{{ t('flows.picksItem') }}</span>
      </div>
      <div class="flex gap-2">
        <PrimaryButton :disabled="!label.trim()" data-testid="flow-step-save" @click="save">
          {{ t('common.save') }}
        </PrimaryButton>
        <SecondaryButton @click="editing = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </div>
</template>
