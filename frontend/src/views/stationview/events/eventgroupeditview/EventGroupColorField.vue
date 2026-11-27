/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {EventGroupDetails} from '@/api/generated/schema'
import {eventGroupColor} from '../eventshared/eventGroupColor'
import {useEventGroupDetails} from './useEventGroupDetails'

/**
 * The colour that marks the parts of an event group. Without a colour of its own the picker shows the
 * one its id picks, and choosing that one keeps it as its own. A new event group has no id yet, so its
 * picker stays neutral and its hint shows that the palette colour is picked once it is saved.
 */
const props = defineProps<{
  /** The event group being edited, or null for a new one; picks the colour shown while none is set. */
  eventGroupId: number | null
}>()

const details = defineModel<EventGroupDetails>({required: true})

const {color} = useEventGroupDetails(details)

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
  <div class="space-y-1">
    <div class="flex items-center gap-1">
      <FieldLabel>{{ t('eventGroups.color') }}</FieldLabel>
      <HintIcon :text="eventGroupId == null && !color ? t('eventGroups.colorHintNew') : t('eventGroups.colorHint')"/>
    </div>
    <div class="flex items-center gap-2">
      <ColorInput v-model="shownColor" data-testid="event-group-color"/>
      <SecondaryButton v-if="color" compact data-testid="event-group-color-reset" @click="color = ''">
        {{ t('eventGroups.colorReset') }}
      </SecondaryButton>
    </div>
  </div>
</template>
