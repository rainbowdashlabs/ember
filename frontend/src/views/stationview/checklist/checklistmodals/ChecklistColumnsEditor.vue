/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'

const columns = defineModel<{label: string; description: string}[]>({required: true})

const {t} = useI18n()

function addColumn() {
  columns.value.push({label: '', description: ''})
}

function removeColumn(index: number) {
  if (columns.value.length === 1) return
  columns.value.splice(index, 1)
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-2">
      <FieldLabel>{{ t('checklist.columns') }}</FieldLabel>
      <SecondaryButton @click="addColumn">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('checklist.addColumn') }}
      </SecondaryButton>
    </div>
    <div class="space-y-2">
      <div v-for="(col, idx) in columns" :key="idx" class="flex items-start gap-2">
        <div class="flex-1 grid sm:grid-cols-2 gap-2">
          <TextInput v-model="col.label" :placeholder="t('checklist.columnLabelPlaceholder')"/>
          <TextInput v-model="col.description" :placeholder="t('checklist.columnDescriptionPlaceholder')"/>
        </div>
        <IconButton
            :icon="['fas', 'trash']"
            :label="t('checklist.removeColumn')"
            :disabled="columns.length === 1"
            @click="removeColumn(idx)"
        />
      </div>
    </div>
  </div>
</template>
