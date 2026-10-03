/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { CommentSource } from '@/api/comments'
import type { BoardLabel, BoardLane, BoardTicketHistoryResponse, BoardTicketTransitionResponse, CommentResponse } from '@/api/generated/schema'
import { contrastTextColor } from '@/util/contrastColor'
import { formatDateTime } from '@/util/format'

/**
 * What happened on a ticket, in three tabs: its comments, its changes, and both together.
 *
 * <p>The comments tab is the shared comment section, kept mounted while another tab shows, so the
 * count on its tab and the comments among the changes stay as current as the thread itself.
 */
const props = defineProps<{
    commentSource: CommentSource
    transitions: BoardTicketTransitionResponse[]
    history: BoardTicketHistoryResponse[]
    lanes: BoardLane[]
    labels: BoardLabel[]
    readonly?: boolean
}>()

const { t, te } = useI18n()
const activeTab = ref('comments')
const comments = ref<CommentResponse[]>([])

function historyActionLabel(action: string): string {
    const key = `boards.historyActions.${action}`
    return te(key) ? t(key) : action
}

type ActivityItem = { type: 'comment'; data: CommentResponse; ts: string } | { type: 'transition'; data: BoardTicketTransitionResponse; ts: string } | { type: 'history'; data: BoardTicketHistoryResponse; ts: string }

const allActivity = computed<ActivityItem[]>(() => {
    const items: ActivityItem[] = [
        ...comments.value.filter(c => !c.deleted).map(c => ({ type: 'comment' as const, data: c, ts: c.createdAt })),
        ...props.transitions.map(t => ({ type: 'transition' as const, data: t, ts: t.movedAt })),
        ...props.history.map(h => ({ type: 'history' as const, data: h, ts: h.createdAt })),
    ]
    return items.sort((a, b) => a.ts.localeCompare(b.ts))
})

const changesActivity = computed<ActivityItem[]>(() => {
    const items: ActivityItem[] = [
        ...props.transitions.map(t => ({ type: 'transition' as const, data: t, ts: t.movedAt })),
        ...props.history.map(h => ({ type: 'history' as const, data: h, ts: h.createdAt })),
    ]
    return items.sort((a, b) => a.ts.localeCompare(b.ts))
})

const priorityIcons: Record<string, { icon: string[]; color: string }> = { HIGHEST: { icon: ['fas', 'angles-up'], color: 'text-red-500' }, HIGH: { icon: ['fas', 'angle-up'], color: 'text-orange-500' }, MEDIUM: { icon: ['fas', 'equals'], color: 'text-yellow-500' }, LOW: { icon: ['fas', 'angle-down'], color: 'text-blue-400' }, LOWEST: { icon: ['fas', 'angles-down'], color: 'text-gray-400' } }
function findLabel(name: string) { return props.labels.find(l => l.name === name) }

const tabs = computed(() => [
    { key: 'comments', label: `${t('boards.comments')} (${comments.value.filter(c => !c.deleted).length})` },
    { key: 'transitions', label: `${t('boards.transitions')} (${props.transitions.length + props.history.length})` },
    { key: 'all', label: t('boards.activityAll') },
])

function activityKey(item: ActivityItem): string {
    return `${item.type}-${item.data.id}`
}

function laneName(id: number | null): string {
    if (id === null) return '-'
    return props.lanes.find(l => l.id === id)?.name ?? `#${id}`
}

</script>

<template>
    <div>
        <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

        <CommentSection
            v-show="activeTab === 'comments'"
            :source="commentSource"
            :readonly="readonly"
            untitled
            @loaded="comments = $event"
        />

        <div v-if="activeTab === 'transitions'" class="space-y-2">
            <template v-for="item in changesActivity" :key="activityKey(item)">
                <div v-if="item.type === 'transition'" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="item.data.actor" size="sm" />
                    <span>{{ t('boards.movedFrom') }}</span>
                    <BaseBadge bg-class="" class="font-medium" :style="{ backgroundColor: lanes.find(l => l.id === item.data.fromLaneId)?.color ?? 'var(--color-primary)', color: contrastTextColor(lanes.find(l => l.id === item.data.fromLaneId)?.color ?? '#fd4f00') }">{{ laneName(item.data.fromLaneId) }}</BaseBadge>
                    <span>{{ t('boards.movedTo') }}</span>
                    <BaseBadge bg-class="" class="font-medium" :style="{ backgroundColor: lanes.find(l => l.id === item.data.toLaneId)?.color ?? 'var(--color-primary)', color: contrastTextColor(lanes.find(l => l.id === item.data.toLaneId)?.color ?? '#fd4f00') }">{{ laneName(item.data.toLaneId) }}</BaseBadge>
                    <span class="ml-auto text-xs">{{ formatDateTime(item.ts) }}</span>
                </div>
                <div v-else-if="item.type === 'history'" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="item.data.actor" size="sm" />
                    <span class="font-medium text-(--text)">{{ historyActionLabel(item.data.action) }}</span>
                    <template v-if="item.data.action === 'PRIORITY_CHANGED' && item.data.detail">
                        <template v-for="(part, i) in (item.data.detail ?? '').split(' → ')" :key="i">
                            <span v-if="i > 0" class="text-(--text-muted)">→</span>
                            <font-awesome-icon v-if="priorityIcons[part]" :icon="priorityIcons[part].icon" :class="priorityIcons[part].color" />
                        </template>
                    </template>
                    <template v-else-if="(item.data.action === 'LABEL_ADDED' || item.data.action === 'LABEL_REMOVED') && item.data.detail">
                        <BaseBadge bg-class="" :style="{ backgroundColor: findLabel(item.data.detail!)?.color ?? '#6b7280', color: contrastTextColor(findLabel(item.data.detail!)?.color ?? '#6b7280') }">{{ item.data.detail }}</BaseBadge>
                    </template>
                    <template v-else-if="item.data.action === 'DUE_DATE_CHANGED' && item.data.detail">
                        <span class="text-(--text)">{{ item.data.detail }}</span>
                    </template>
                    <span v-else-if="item.data.detail" class="text-(--text)">{{ item.data.detail }}</span>
                    <span class="ml-auto text-xs">{{ formatDateTime(item.ts) }}</span>
                </div>
            </template>
            <p v-if="changesActivity.length === 0" class="text-sm text-(--text-muted) text-center py-4">-</p>
        </div>

        <div v-if="activeTab === 'all'" class="space-y-3">
            <div v-for="item in allActivity" :key="`${item.type}-${item.data.id}`">
                <div v-if="item.type === 'comment'" class="flex gap-2">
                    <div class="flex-1">
                        <div class="flex items-center gap-2 text-xs text-(--text-muted)">
                            <MemberName :identity="item.data.author" size="sm" class="font-medium" />
                            <span>{{ formatDateTime(item.ts) }}</span>
                        </div>
                        <p class="text-sm mt-0.5 whitespace-pre-wrap">{{ item.data.content }}</p>
                    </div>
                </div>
                <div v-else-if="item.type === 'transition'" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="item.data.actor" size="sm" />
                    <span>{{ t('boards.movedFrom') }}</span>
                    <BaseBadge bg-class="" class="font-medium" :style="{ backgroundColor: lanes.find(l => l.id === item.data.fromLaneId)?.color ?? 'var(--color-primary)', color: contrastTextColor(lanes.find(l => l.id === item.data.fromLaneId)?.color ?? '#fd4f00') }">{{ laneName(item.data.fromLaneId) }}</BaseBadge>
                    <span>{{ t('boards.movedTo') }}</span>
                    <BaseBadge bg-class="" class="font-medium" :style="{ backgroundColor: lanes.find(l => l.id === item.data.toLaneId)?.color ?? 'var(--color-primary)', color: contrastTextColor(lanes.find(l => l.id === item.data.toLaneId)?.color ?? '#fd4f00') }">{{ laneName(item.data.toLaneId) }}</BaseBadge>
                    <span class="ml-auto text-xs">{{ formatDateTime(item.ts) }}</span>
                </div>
                <div v-else-if="item.type === 'history'" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="item.data.actor" size="sm" />
                    <span class="font-medium text-(--text)">{{ historyActionLabel(item.data.action) }}</span>
                    <template v-if="item.data.action === 'PRIORITY_CHANGED' && item.data.detail">
                        <template v-for="(part, i) in (item.data.detail ?? '').split(' → ')" :key="i">
                            <span v-if="i > 0" class="text-(--text-muted)">→</span>
                            <font-awesome-icon v-if="priorityIcons[part]" :icon="priorityIcons[part].icon" :class="priorityIcons[part].color" />
                        </template>
                    </template>
                    <template v-else-if="(item.data.action === 'LABEL_ADDED' || item.data.action === 'LABEL_REMOVED') && item.data.detail">
                        <BaseBadge bg-class="" :style="{ backgroundColor: findLabel(item.data.detail!)?.color ?? '#6b7280', color: contrastTextColor(findLabel(item.data.detail!)?.color ?? '#6b7280') }">{{ item.data.detail }}</BaseBadge>
                    </template>
                    <span v-else-if="item.data.detail" class="text-(--text)">{{ item.data.detail }}</span>
                    <span class="ml-auto text-xs">{{ formatDateTime(item.ts) }}</span>
                </div>
            </div>
            <p v-if="allActivity.length === 0" class="text-sm text-(--text-muted) text-center py-4">-</p>
        </div>

    </div>
</template>
