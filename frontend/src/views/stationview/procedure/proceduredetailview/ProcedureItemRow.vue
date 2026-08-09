/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {ProcedureItem} from '@/api/procedures'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()

const props = defineProps<{
  item: ProcedureItem
  canEdit: boolean
  canCheck: boolean
  dependencyMet: boolean
  dependencyNames: string[]
}>()

const emit = defineEmits<{
  toggle: []
  'update-note': [note: string]
}>()

function onNoteBlur(e: Event) {
  emit('update-note', (e.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <component :is="item.checked ? SuccessContainer : NeutralContainer">
    <div class="flex items-start gap-3">
      <div class="pt-0.5 shrink-0">
        <IconButton
            v-if="canCheck"
            :icon="item.checked ? ['fas', 'square-check'] : ['fas', 'square']"
            :label="item.checked ? t('procedures.uncheck') : t('procedures.check')"
            :class="item.checked ? 'text-[var(--success)]' : 'text-[var(--text-muted)]'"
            @click="emit('toggle')"
        />
        <font-awesome-icon
            v-else-if="item.checked"
            :icon="['fas', 'square-check']"
            class="w-4 h-4 text-[var(--success)] mt-1 ml-2"
        />
        <font-awesome-icon
            v-else
            :icon="['fas', 'lock']"
            class="w-4 h-4 text-[var(--text-muted)] opacity-50 mt-1 ml-2"
            :title="!dependencyMet ? t('procedures.locked') + ': ' + dependencyNames.join(', ') : t('procedures.editOnly')"
        />
      </div>
      <div class="flex-1 min-w-0">
        <div class="font-medium" :class="{ 'line-through opacity-60': item.checked }">{{ item.title }}</div>
        <div v-if="item.description" class="text-sm text-[var(--text-muted)]">{{ item.description }}</div>
        <div v-if="!dependencyMet && !item.checked"
             class="text-xs text-[var(--text-muted)] mt-1 flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'lock']" class="w-3 h-3"/>
          {{ t('procedures.dependsOn') }}: {{ dependencyNames.join(', ') }}
        </div>
        <div v-if="item.checkedAt" class="text-xs text-[var(--text-muted)] mt-1">
          {{ t('procedures.checkedBy') }}: {{ formatDateTime(item.checkedAt) }}
        </div>
        <div v-if="item.note || (!item.checked && canEdit)" class="mt-2">
          <TextAreaInput
              v-if="!item.checked && canEdit"
              :model-value="item.note ?? ''"
              :placeholder="t('procedures.itemNote')"
              class="!text-xs !py-1 !min-h-0"
              :rows="1"
              @blur="onNoteBlur"
          />
          <p v-else-if="item.note" class="text-sm text-[var(--text-muted)] italic">{{ item.note }}</p>
        </div>
      </div>
    </div>
  </component>
</template>
