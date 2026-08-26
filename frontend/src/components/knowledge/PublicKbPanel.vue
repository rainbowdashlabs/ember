/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const mode = defineModel<string>('mode', {required: true})

const props = defineProps<{
  publicUrl: string
}>()

const {t} = useI18n()
const enabled = computed(() => mode.value !== 'OFF')

function toggle() {
  mode.value = enabled.value ? 'OFF' : 'ALLOW_ALL'
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('stationManage.publicKb.title') }}</SubHeader>
    <MutedText size="sm">{{ t('stationManage.publicKb.hint') }}</MutedText>
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ t('stationManage.publicKb.enabled') }}</span>
      <ToggleInput :model-value="enabled" @update:model-value="toggle"/>
    </div>
    <div v-if="enabled" class="space-y-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.publicKb.mode') }}</FieldLabel>
        <SelectInput v-model="mode" class="w-full">
          <option value="ALLOW_ALL">{{ t('stationManage.publicKb.modeAllowAll') }}</option>
          <option value="DENY_ALL">{{ t('stationManage.publicKb.modeDenyAll') }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.publicKb.publicUrl') }}</FieldLabel>
        <code
            class="block rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">
          {{ props.publicUrl }}
        </code>
      </div>
    </div>
  </NeutralContainer>
</template>
