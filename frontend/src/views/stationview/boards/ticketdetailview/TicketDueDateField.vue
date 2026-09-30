/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import DateInput from '@/components/input/datetime/DateInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { formatDate } from '@/util/format'

const props = defineProps<{
    canEdit: boolean
}>()

const dueDate = defineModel<string>('dueDate', { default: '' })
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
    open: []
}>()

const { t } = useI18n()

/** Opens the date field in place of the date, for whoever may change it. */
function startEditing() {
    if (!props.canEdit) return
    emit('open')
    editing.value = true
}
</script>

<template>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
        <DateInput v-if="editing && canEdit" v-model="dueDate" @change="editing = false; emit('save')" @blur="editing = false" />
        <div
            v-else
            class="rounded-theme px-2 py-1 text-sm"
            :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''"
            role="button"
            tabindex="0"
            :aria-disabled="!canEdit"
            @click.stop="startEditing"
            @keydown.enter.stop.prevent="startEditing"
            @keydown.space.stop.prevent="startEditing"
        >
            <span v-if="dueDate">{{ formatDate(dueDate) }}</span>
            <span v-else class="text-(--text-muted) italic">-</span>
        </div>
    </div>
</template>
