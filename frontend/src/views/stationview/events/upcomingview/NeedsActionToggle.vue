/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

/** Cutting the list down to the dates that are still waiting for an answer. */
const needsAction = defineModel<boolean>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('eventsUpcoming.needsAction') }}</FieldLabel>
    <div class="flex items-center px-3 py-2">
      <ToggleInput v-model="needsAction"/>
    </div>
  </div>
</template>
