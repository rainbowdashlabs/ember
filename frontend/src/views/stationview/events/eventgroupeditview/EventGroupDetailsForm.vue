/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {eventGroupColor} from '../eventshared/eventGroupColor'
import {nonNegative} from '../templateeditview/signUpRules'
import type {EventGroupDetails} from '@/api/generated/schema'
import {instantToLocalInput, localInputToInstant} from '@/util/format'

/**
 * What an event group holds besides its sets, audiences and parts: name, description, its sign-up, the
 * minimum of accepted sign-ups with the days before the first part it must be reached by, and the
 * colour that marks its parts. The days are only asked once a minimum is given. Without a colour of its own the picker shows the one its id picks,
 * and choosing that one keeps it as its own. A new event group has no id yet, so its picker stays
 * neutral and says that the palette colour is picked once it is saved. The details come and go as one
 * value in the shape the server keeps them, the deadline as an instant.
 */
const props = defineProps<{
  /** The event group being edited, or null for a new one; picks the colour shown while none is set. */
  eventGroupId: number | null
}>()

/** The details as the server keeps them; each field below reads and writes its own part. */
const details = defineModel<EventGroupDetails>({required: true})

function update(change: Partial<EventGroupDetails>) {
  details.value = {...details.value, ...change}
}

const name = computed({get: () => details.value.name ?? '', set: (value: string) => update({name: value})})
const description = computed({get: () => details.value.description ?? '', set: (value: string) => update({description: value})})
const limit = computed({
  get: () => details.value.registrationLimit ?? undefined,
  set: (value: number | undefined) => update({registrationLimit: value ?? null}),
})
const deadline = computed({
  get: () => instantToLocalInput(details.value.registrationDeadline),
  set: (value: string) => update({registrationDeadline: value ? localInputToInstant(value) || null : null}),
})
const confirm = computed({
  get: () => details.value.requiresConfirmation ?? false,
  set: (value: boolean) => update({requiresConfirmation: value}),
})
const color = computed({get: () => details.value.color ?? '', set: (value: string) => update({color: value || null})})
const minRegistrations = computed({
  get: () => details.value.minRegistrations ?? undefined,
  set: (value: number | undefined) => update({minRegistrations: value ?? null}),
})
const thresholdDays = computed({
  get: () => details.value.thresholdDays ?? undefined,
  set: (value: number | undefined) => update({thresholdDays: value ?? null}),
})

const NEUTRAL = '#9ca3af'

const shownColor = computed({
  get: () => color.value || (props.eventGroupId == null ? NEUTRAL : eventGroupColor(props.eventGroupId, null)),
  set: (value: string) => {
    color.value = value
  },
})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <FieldLabel for="event-group-name">{{ t('eventGroups.name') }}</FieldLabel>
    <TextInput id="event-group-name" v-model="name" data-testid="event-group-name"/>
    <FieldLabel>{{ t('eventGroups.description') }}</FieldLabel>
    <MarkdownEditor v-model="description"/>
    <FieldLabel for="event-group-limit">{{ t('eventGroups.limit') }}</FieldLabel>
    <NumberInput id="event-group-limit" v-model="limit"/>
    <FieldHint>{{ t('eventGroups.limitHint') }}</FieldHint>
    <FieldLabel for="event-group-deadline">{{ t('eventGroups.deadlineField') }}</FieldLabel>
    <DateTimeInput id="event-group-deadline" v-model="deadline"/>
    <FieldLabel inline>
      <ToggleInput v-model="confirm"/>
      {{ t('eventGroups.confirmationField') }}
    </FieldLabel>
    <FieldLabel for="event-group-minimum">{{ t('eventGroups.minimumField') }}</FieldLabel>
    <NumberInput
        id="event-group-minimum"
        :model-value="minRegistrations"
        min="0"
        data-testid="event-group-minimum"
        @update:model-value="value => minRegistrations = nonNegative(value)"
    />
    <template v-if="minRegistrations">
      <FieldLabel for="event-group-threshold">{{ t('eventGroups.thresholdField') }}</FieldLabel>
      <NumberInput
          id="event-group-threshold"
          :model-value="thresholdDays"
          min="0"
          :placeholder="t('events.thresholdDaysPlaceholder')"
          data-testid="event-group-threshold"
          @update:model-value="value => thresholdDays = nonNegative(value)"
      />
      <FieldHint>{{ t('eventGroups.minimumHint') }}</FieldHint>
    </template>
    <FieldLabel>{{ t('eventGroups.color') }}</FieldLabel>
    <div class="flex items-center gap-2">
      <ColorInput v-model="shownColor" data-testid="event-group-color"/>
      <SecondaryButton v-if="color" compact data-testid="event-group-color-reset" @click="color = ''">
        {{ t('eventGroups.colorReset') }}
      </SecondaryButton>
    </div>
    <FieldHint>{{ eventGroupId == null && !color ? t('eventGroups.colorHintNew') : t('eventGroups.colorHint') }}</FieldHint>
  </NeutralContainer>
</template>
