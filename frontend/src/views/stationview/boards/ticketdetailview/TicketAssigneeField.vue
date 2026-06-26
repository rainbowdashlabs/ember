/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import type { BoardTicket } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

defineProps<{
    ticket: BoardTicket
    members: MemberCompletion[]
    canEdit: boolean
}>()

const assignedMemberId = defineModel<string>('assignedMemberId', { default: '' })
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
    open: []
}>()

const { t } = useI18n()
</script>

<template>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
        <MemberSelectInput v-if="editing && canEdit" v-model="assignedMemberId" :members="members" :placeholder="t('boards.unassigned')" auto-open @change="editing = false; emit('save')" />
        <div v-else class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm" :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''" @click.stop="canEdit && (emit('open'), editing = true)">
            <span v-if="ticket.assignee" class="flex items-center gap-2">
                <UserAvatar :identity="ticket.assignee" size="sm" />
                {{ members.find(m => m.memberUid === ticket.assignee?.memberUid)?.name ?? ticket.assignee.displayTag?.name }}
            </span>
            <span v-else class="text-(--text-muted) italic">{{ t('boards.unassigned') }}</span>
        </div>
    </div>
</template>
