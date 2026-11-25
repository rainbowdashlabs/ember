/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import type {AppointmentField, EventFieldEntry} from '@/api/generated/schema'

/**
 * The fields an appointment takes over from its event group, read-only here. "Override" copies one
 * into the appointment's own fields, which then replaces it for this appointment; removing the own
 * field again brings the event group's value back.
 *
 * <p>Fields the event group ties to its attendance sheet fill in nothing on an appointment that takes
 * a sheet of its own, which a hint points out.
 */
const props = defineProps<{
  inherited: AppointmentField[]
  /** The appointment's own fields as the editor holds them, which decide what is replaced. */
  own: EventFieldEntry[]
  /** Whether the appointment takes another attendance sheet than its event group. */
  ownSheet?: boolean
}>()

const emit = defineEmits<{
  override: [field: AppointmentField]
}>()

const {t} = useI18n()

const ownNames = computed(() => new Set(props.own.map(field => (field.name ?? '').trim().toLowerCase())))

const tiesFillNothing = computed(() =>
    props.ownSheet === true && props.inherited.some(field => field.attendanceFieldId != null))

function replaced(field: AppointmentField): boolean {
  return ownNames.value.has(field.name.trim().toLowerCase())
}
</script>

<template>
  <NeutralContainer v-if="inherited.length > 0" class="space-y-3" data-testid="inherited-fields">
    <SectionHeader>{{ t('eventGroups.inheritedFields') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.inheritedFieldsHint') }}</MutedText>
    <MutedText v-if="tiesFillNothing" tag="p" size="sm" data-testid="inherited-fields-own-sheet">
      {{ t('eventGroups.inheritedFieldsOwnSheet') }}
    </MutedText>
    <div v-for="field in inherited" :key="field.id" class="flex flex-wrap items-center justify-between gap-2"
         data-testid="inherited-field">
      <div class="min-w-0">
        <DetailLabel>{{ field.name }}</DetailLabel>
        <p class="text-sm" :class="{'line-through text-(--text-muted)': replaced(field)}">
          <QuestionValueDisplay :field-type="field.fieldType" :value="field.value"/>
        </p>
      </div>
      <MutedText v-if="replaced(field)" tag="span" size="sm">{{ t('eventGroups.overridden') }}</MutedText>
      <SecondaryButton v-else :icon="['fas', 'pen']" compact data-testid="override-field" @click="emit('override', field)">
        {{ t('eventGroups.override') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
