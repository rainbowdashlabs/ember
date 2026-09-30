/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import InlineEditTrigger from '@/components/button/InlineEditTrigger.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { fromCompletion } from '@/components/input/select/memberOption'
import type { BoardTicket } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

const props = defineProps<{
    ticket: BoardTicket
    /** Everybody the station has, which is what puts a name on whoever is already on the ticket. */
    members: MemberCompletion[]
    /** Whom the ticket may be handed to, which is who the picker offers. */
    assignableMembers: MemberCompletion[]
    canEdit: boolean
}>()

const assignedMemberId = defineModel<string>('assignedMemberId', { default: '' })
const editing = defineModel<boolean>('editing', { default: false })

const emit = defineEmits<{
    save: []
    open: []
}>()

const { t } = useI18n()

const assignable = computed(() => props.assignableMembers.map(fromCompletion))

/** Opens the picker in place of the name, for whoever may change it. */
function startEditing() {
    if (!props.canEdit) return
    emit('open')
    editing.value = true
}
</script>

<template>
    <div data-testid="ticket-assignee">
        <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
        <MemberSelectInput v-if="editing && canEdit" v-model="assignedMemberId" :members="assignable" :placeholder="t('boards.unassigned')" clearable auto-open @change="editing = false; emit('save')" />
        <InlineEditTrigger v-else :can-edit="canEdit" class="flex items-center gap-2" @activate="startEditing">
            <span v-if="ticket.assignee" class="flex items-center gap-2">
                <UserAvatar :identity="ticket.assignee" size="sm" />
                {{ members.find(m => m.memberUid === ticket.assignee?.memberUid)?.name ?? ticket.assignee.displayTag?.name }}
            </span>
            <span v-else class="text-(--text-muted) italic">{{ t('boards.unassigned') }}</span>
        </InlineEditTrigger>
    </div>
</template>
