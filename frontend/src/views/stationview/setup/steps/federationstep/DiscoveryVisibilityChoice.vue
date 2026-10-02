/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {DiscoveryVisibility, type DiscoveryVisibilityName} from '@/api/stationManage'

/** The three places a station can be listed, each with what it means written beside it. */
const visibility = defineModel<DiscoveryVisibilityName>({required: true})

const {t} = useI18n()

const options = computed(() => [
  {value: DiscoveryVisibility.PUBLIC, label: t('setup.steps.federation.visibilityPublic'), hint: t('setup.steps.federation.visibilityPublicHint')},
  {value: DiscoveryVisibility.INSTANCE, label: t('setup.steps.federation.visibilityInstance'), hint: t('setup.steps.federation.visibilityInstanceHint')},
  {value: DiscoveryVisibility.NONE, label: t('setup.steps.federation.visibilityNone'), hint: t('setup.steps.federation.visibilityNoneHint')},
])
</script>

<template>
  <fieldset class="space-y-2">
    <legend class="text-sm font-medium mb-1">{{ t('setup.steps.federation.visibility') }}</legend>
    <label
        v-for="option in options"
        :key="option.value"
        :data-testid="`visibility-${option.value}`"
        class="flex items-start gap-2 cursor-pointer"
    >
      <RadioInput v-model="visibility" :value="option.value" class="mt-1"/>
      <span class="space-y-0.5">
        <span class="block text-sm">{{ option.label }}</span>
        <MutedText tag="span" size="xs" class="block">{{ option.hint }}</MutedText>
      </span>
    </label>
  </fieldset>
</template>
