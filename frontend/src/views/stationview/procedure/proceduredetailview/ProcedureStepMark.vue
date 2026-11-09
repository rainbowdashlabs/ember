/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {ProcedureItem} from '@/api/generated/schema'

/**
 * The mark in front of a step: a box the reader ticks, a document waiting for signatures, a tick, or a
 * lock while the step waits for others or is not the reader's to tick.
 */
const {t} = useI18n()

defineProps<{
  item: ProcedureItem
  canCheck: boolean
  dependencyMet: boolean
  dependencyNames: string[]
}>()

const emit = defineEmits<{
  toggle: []
}>()
</script>

<template>
  <div class="pt-0.5 shrink-0">
    <IconButton
        v-if="canCheck"
        :icon="item.checked ? ['fas', 'square-check'] : ['fas', 'square']"
        :label="item.checked ? t('procedures.uncheck') : t('procedures.check')"
        :class="item.checked ? 'text-[var(--color-success)]' : 'text-[var(--text-muted)]'"
        @click="emit('toggle')"
    />
    <font-awesome-icon
        v-else-if="item.kind === 'DOCUMENT' && !item.checked && dependencyMet"
        :icon="['fas', 'file-signature']"
        class="w-4 h-4 text-[var(--text-muted)] mt-1 ml-2"
    />
    <font-awesome-icon
        v-else-if="item.checked"
        :icon="['fas', 'square-check']"
        class="w-4 h-4 text-[var(--color-success)] mt-1 ml-2"
    />
    <font-awesome-icon
        v-else
        :icon="['fas', 'lock']"
        class="w-4 h-4 text-[var(--text-muted)] opacity-50 mt-1 ml-2"
        :title="!dependencyMet ? t('procedures.locked') + ': ' + dependencyNames.join(', ') : t('procedures.editOnly')"
    />
  </div>
</template>
