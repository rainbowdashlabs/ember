/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconSelectInput from '@/components/input/select/IconSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import InlineEditTrigger from '@/components/button/InlineEditTrigger.vue'
import type { TicketPriority } from '@/api/generated/schema'
import type {PriorityOption} from './types'


const props = defineProps<{
    options: PriorityOption[]
    canEdit: boolean
}>()

const priority = defineModel<TicketPriority>('priority')
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
    open: []
}>()

const { t } = useI18n()

/** Opens the picker in place of the priority, for whoever may change it. */
function startEditing() {
    if (!props.canEdit) return
    emit('open')
    editing.value = true
}
</script>

<template>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
        <IconSelectInput v-if="editing && canEdit" v-model="priority" :options="options" auto-open @update:model-value="editing = false; emit('save')" />
        <InlineEditTrigger v-else :can-edit="canEdit" class="flex items-center gap-2" @activate="startEditing">
            <font-awesome-icon :icon="options.find(o => o.value === priority)?.icon ?? ['fas', 'equals']" :class="options.find(o => o.value === priority)?.color" />
            <span>{{ options.find(o => o.value === priority)?.label }}</span>
        </InlineEditTrigger>
    </div>
</template>
