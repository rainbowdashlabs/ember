/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ProcedureTemplate} from '@/api/procedures'

const {t} = useI18n()

defineProps<{
  templates: ProcedureTemplate[]
  selectedTemplateId: number | null
}>()

const emit = defineEmits<{
  change: [id: string | undefined]
}>()
</script>

<template>
  <NeutralContainer>
    <FieldLabel class="mb-2">{{ t('procedures.loadFromTemplate') }}</FieldLabel>
    <SelectInput
        :model-value="selectedTemplateId != null ? String(selectedTemplateId) : ''"
        class="w-full"
        @update:model-value="emit('change', $event || undefined)"
    >
      <option value="">{{ t('procedures.noTemplate') }}</option>
      <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
    </SelectInput>
    <MutedText size="sm" class="mt-1">{{ t('procedures.templateHint') }}</MutedText>
  </NeutralContainer>
</template>
