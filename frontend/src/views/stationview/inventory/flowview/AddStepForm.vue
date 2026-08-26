/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FlowField from './FlowField.vue'
import {ItemCustody, type ItemCustodyName} from '@/api/inventory'
import {StepActor, StepSubject, type StepActorName, type StepRequest, type StepSubjectName} from '@/api/movements'

const {t} = useI18n()

defineProps<{busy: boolean}>()

const emit = defineEmits<{add: [step: StepRequest]}>()

const label = ref('')
const actor = ref<StepActorName>(StepActor.STATION)
const subject = ref<StepSubjectName>(StepSubject.OUTGOING)
const custodyAfter = ref<ItemCustodyName>(ItemCustody.AT_STATION)
const picksItem = ref(false)

/**
 * The custodies a step may leave an item in. Lending has its own flow and losing something is not a
 * step anybody takes, so neither is offered here.
 */
const CUSTODIES: ItemCustodyName[] = [
  ItemCustody.WITH_OWNER,
  ItemCustody.AT_STATION,
  ItemCustody.WITH_MEMBER,
  ItemCustody.IN_TRANSIT,
]

function submit() {
  if (!label.value.trim()) return
  emit('add', {
    label: label.value.trim(),
    actor: actor.value,
    subject: subject.value,
    custodyAfter: custodyAfter.value,
    picksItem: picksItem.value,
  })
  label.value = ''
  picksItem.value = false
}
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-2">
    <FlowField class="sm:col-span-2" :label="t('flows.stepLabel')">
      <TextInput v-model="label" :placeholder="t('flows.stepLabelPlaceholder')"/>
    </FlowField>
    <FlowField :label="t('flows.stepActor')">
      <SelectInput v-model="actor">
        <option v-for="value in [StepActor.MEMBER, StepActor.STATION, StepActor.OWNER]" :key="value" :value="value">
          {{ t(`movements.actor.${value}`) }}
        </option>
      </SelectInput>
    </FlowField>
    <FlowField :label="t('flows.stepSubject')">
      <SelectInput v-model="subject">
        <option v-for="value in [StepSubject.OUTGOING, StepSubject.INCOMING]" :key="value" :value="value">
          {{ t(`movements.subject.${value}`) }}
        </option>
      </SelectInput>
    </FlowField>
    <FlowField :label="t('flows.stepCustody')">
      <SelectInput v-model="custodyAfter">
        <option v-for="value in CUSTODIES" :key="value" :value="value">
          {{ t(`itemDetail.custodyValues.${value}`) }}
        </option>
      </SelectInput>
    </FlowField>
    <FlowField :label="t('flows.picksItem')">
      <CheckboxInput v-model="picksItem"/>
    </FlowField>
    <div class="sm:col-span-2">
      <PrimaryButton :disabled="busy || !label.trim()" @click="submit">{{ t('flows.addStep') }}</PrimaryButton>
    </div>
  </div>
</template>
