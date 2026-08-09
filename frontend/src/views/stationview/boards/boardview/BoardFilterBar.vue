/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import IconButton from '@/components/button/IconButton.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import BoardAssigneeFilter from './BoardAssigneeFilter.vue'
import type { BoardLabel } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

const assigneeFilter = defineModel<Set<string>>('assigneeFilter', { required: true })
const labelFilter = defineModel<string[]>('labelFilter', { required: true })

const props = defineProps<{
    shortKey: string
    hasBacklog: boolean
    assignees: MemberCompletion[]
    labels: BoardLabel[]
}>()

const { t } = useI18n()
const router = useRouter()

const labelFilterOptions = computed(() => props.labels.map(l => ({ value: String(l.id), label: l.name })))

function openBacklog() {
    router.push(`/station/boards/${props.shortKey}/backlog`)
}
</script>

<template>
    <div class="flex items-center mb-4 gap-3">
        <IconButton v-if="hasBacklog" :icon="['fas', 'inbox']" :label="t('boards.showBacklog')" class="text-(--text-muted)" @click="openBacklog" />
        <BoardAssigneeFilter v-model="assigneeFilter" :assignees="assignees" />
        <MultiSelectDropdown
            v-if="labels.length > 0"
            v-model="labelFilter"
            :options="labelFilterOptions"
            :placeholder="t('boards.labels')"
        />
    </div>
</template>
