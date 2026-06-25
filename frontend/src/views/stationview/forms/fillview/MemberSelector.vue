/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

type Option = { id: number | null; label: string }

defineProps<{
  options: Option[]
}>()

const selected = defineModel<number | null>({ required: true })

const { t } = useI18n()
</script>

<template>
  <InfoContainer>
    <div class="space-y-2">
      <p class="text-sm font-medium">{{ t('forms.fillForWhom') }}</p>
      <SelectInput
          :model-value="String(selected ?? '')"
          @update:model-value="(v: string | undefined) => selected = v ? Number(v) : null"
      >
        <option v-for="opt in options" :key="String(opt.id)" :value="String(opt.id ?? '')">
          {{ opt.label }}
        </option>
      </SelectInput>
      <p v-if="selected" class="text-xs text-(--text-muted)">
        {{ t('forms.fillForMemberHint') }}
      </p>
    </div>
  </InfoContainer>
</template>
