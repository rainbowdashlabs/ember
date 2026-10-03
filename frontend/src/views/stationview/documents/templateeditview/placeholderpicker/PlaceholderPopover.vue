/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Popover from '@/components/feedback/Popover.vue'
import type {Placeholder} from '@/api/generated/schema'
import type {PlaceholderChoice} from './placeholderKey'
import PlaceholderPicker from './PlaceholderPicker.vue'

/**
 * The placeholder picker behind a button, for a place too narrow to show it open, such as a field on
 * a PDF or the title of a template. It closes once a placeholder is picked.
 */
defineProps<{
  placeholders: Placeholder[]
  legal: boolean
}>()

const emit = defineEmits<{
  pick: [choice: PlaceholderChoice]
}>()

const {t} = useI18n()

function pick(choice: PlaceholderChoice, close: () => void) {
  emit('pick', choice)
  close()
}
</script>

<template>
  <Popover :label="t('documentTemplates.insertPlaceholder')" role="dialog" test-id="placeholder-popover"
           panel-class="w-[min(36rem,calc(100vw-16px))] p-3">
    <template #trigger="{toggle, triggerAttrs}">
      <SecondaryButton compact :icon="['fas', 'plus']" data-testid="placeholder-popover-trigger" v-bind="triggerAttrs"
                       @click="toggle">
        {{ t('documentTemplates.insertPlaceholder') }}
      </SecondaryButton>
    </template>
    <template #default="{close}">
      <PlaceholderPicker :placeholders="placeholders" :legal="legal" @pick="choice => pick(choice, close)"/>
    </template>
  </Popover>
</template>
