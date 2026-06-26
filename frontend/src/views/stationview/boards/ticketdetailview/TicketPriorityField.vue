/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconSelectInput from '@/components/input/select/IconSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { TicketPriorityName } from '@/api/boards'
import type {PriorityOption} from './types'


defineProps<{
    options: PriorityOption[]
    canEdit: boolean
}>()

const priority = defineModel<TicketPriorityName>('priority')
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
    open: []
}>()

const { t } = useI18n()
</script>

<template>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
        <IconSelectInput v-if="editing && canEdit" v-model="priority" :options="options" auto-open @update:model-value="editing = false; emit('save')" />
        <div v-else class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm" :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''" @click.stop="canEdit && (emit('open'), editing = true)">
            <font-awesome-icon :icon="options.find(o => o.value === priority)?.icon ?? ['fas', 'equals']" :class="options.find(o => o.value === priority)?.color" />
            <span>{{ options.find(o => o.value === priority)?.label }}</span>
        </div>
    </div>
</template>
