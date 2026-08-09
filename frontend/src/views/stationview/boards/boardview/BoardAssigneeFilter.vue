/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import type { MemberCompletion } from '@/api/stationMembers'

const selected = defineModel<Set<string>>({ required: true })

defineProps<{
    assignees: MemberCompletion[]
}>()

const { t } = useI18n()

function toggle(memberUid: string) {
    const next = new Set(selected.value)
    if (next.has(memberUid)) next.delete(memberUid); else next.add(memberUid)
    selected.value = next
}
</script>

<template>
    <div v-if="assignees.length > 0" class="flex items-center">
        <div class="cursor-pointer rounded-full transition-all" :class="selected.size === 0 ? 'ring-2 ring-primary' : 'opacity-60 hover:opacity-100'" :title="t('boards.allMembers')" @click="selected = new Set()">
            <div class="h-8 w-8 rounded-full bg-primary/15 text-primary font-bold flex items-center justify-center text-xs">
                <font-awesome-icon :icon="['fas', 'users']" />
            </div>
        </div>
        <div v-for="member in assignees" :key="member.id" class="cursor-pointer rounded-full transition-all -ml-1.5" :class="selected.has(member.memberUid) ? 'ring-2 ring-primary z-10' : 'opacity-70 hover:opacity-100'" :title="member.name" @click="toggle(member.memberUid)">
            <UserAvatar :identity="member" :name="member.name" size="md" />
        </div>
    </div>
</template>
