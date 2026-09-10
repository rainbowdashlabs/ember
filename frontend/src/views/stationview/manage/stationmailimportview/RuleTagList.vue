/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RemovableChip from './RemovableChip.vue'

/** The words a rule puts on everything it files. */
const tags = defineModel<string[]>({required: true})

const {t} = useI18n()

const typed = ref('')

function add() {
  const candidate = typed.value.trim()
  if (!candidate) return
  if (!tags.value.includes(candidate)) tags.value = [...tags.value, candidate]
  typed.value = ''
}
</script>

<template>
  <div class="space-y-2">
    <FieldLabel hint>{{ t('mailImport.rule.tags') }}</FieldLabel>
    <div class="flex flex-wrap gap-2">
      <RemovableChip v-for="tag in tags" :key="tag" :label="tag"
                     @remove="tags = tags.filter(candidate => candidate !== tag)"/>
    </div>
    <div class="flex items-start gap-2">
      <TextInput v-model="typed" class="min-w-0 flex-1" data-testid="rule-tag" @keyup.enter="add"/>
      <SecondaryButton :disabled="typed.trim() === ''" @click="add">{{ t('common.add') }}</SecondaryButton>
    </div>
  </div>
</template>
