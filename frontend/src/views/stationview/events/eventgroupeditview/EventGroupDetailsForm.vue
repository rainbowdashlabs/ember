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

/**
 * What an event group holds besides its sets, audiences and parts: name, description, its sign-up and
 * the colour that marks its parts. Without a colour of its own the picker shows the one its id picks,
 * and choosing that one keeps it as its own. A new event group has no id yet, so its picker stays
 * neutral and says that the palette colour is picked once it is saved.
 */
const props = defineProps<{
  /** The event group being edited, or null for a new one; picks the colour shown while none is set. */
  eventGroupId: number | null
}>()

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const limit = defineModel<number | undefined>('limit', {required: true})
const deadline = defineModel<string>('deadline', {required: true})
const confirm = defineModel<boolean>('confirm', {required: true})
const color = defineModel<string>('color', {required: true})

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
