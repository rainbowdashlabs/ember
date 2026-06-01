/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import CommentThread from '@/components/comment/CommentThread.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { BoardComment, BoardTicketTransition, BoardTicketHistoryEntry, BoardLane, BoardLabel } from '@/api/boards'
import type { Comment } from '@/api/types'
import type { MemberCompletion } from '@/api/stationMembers'
import { contrastTextColor } from '@/theme/contrast'

const props = defineProps<{
    comments: BoardComment[]
    transitions: BoardTicketTransition[]
    history: BoardTicketHistoryEntry[]
    lanes: BoardLane[]
    labels: BoardLabel[]
    members: MemberCompletion[]
    readonly?: boolean
    federated?: boolean
}>()

const emit = defineEmits<{
    createComment: [parentId: number | null, content: string]
    updateComment: [commentId: number, content: string]
    deleteComment: [commentId: number]
}>()

const { t } = useI18n()
const activeTab = ref('comments')

const commentsAsGeneric = computed<Comment[]>(() =>
    props.comments.map(c => ({
        id: c.id,
        parentId: c.parentId,
        author: c.author,
        authorName: c.authorName,
        content: c.content,
        deleted: c.deleted,
        createdAt: c.createdAt,
        updatedAt: c.updatedAt,
    })),
)

type ActivityItem = { type: 'comment'; data: BoardComment; ts: string } | { type: 'transition'; data: BoardTicketTransition; ts: string } | { type: 'history'; data: BoardTicketHistoryEntry; ts: string }

const allActivity = computed<ActivityItem[]>(() => {
    const items: ActivityItem[] = [
        ...props.comments.filter(c => !c.deleted).map(c => ({ type: 'comment' as const, data: c, ts: c.createdAt })),
        ...props.transitions.map(t => ({ type: 'transition' as const, data: t, ts: t.movedAt })),
        ...props.history.map(h => ({ type: 'history' as const, data: h, ts: h.createdAt })),
    ]
    return items.sort((a, b) => a.ts.localeCompare(b.ts))
})

const historyActionLabel: Record<string, string> = { PRIORITY_CHANGED: 'Priorität', LABEL_ADDED: 'Label hinzugefügt', LABEL_REMOVED: 'Label entfernt', TITLE_CHANGED: 'Titel geändert', DESCRIPTION_CHANGED: 'Beschreibung geändert', DUE_DATE_CHANGED: 'Fälligkeitsdatum', FIELD_CHANGED: 'Feld geändert' }
const priorityIcons: Record<string, { icon: string[]; color: string }> = { HIGHEST: { icon: ['fas', 'angles-up'], color: 'text-red-500' }, HIGH: { icon: ['fas', 'angle-up'], color: 'text-orange-500' }, MEDIUM: { icon: ['fas', 'equals'], color: 'text-yellow-500' }, LOW: { icon: ['fas', 'angle-down'], color: 'text-blue-400' }, LOWEST: { icon: ['fas', 'angles-down'], color: 'text-gray-400' } }
function findLabel(name: string) { return props.labels.find(l => l.name === name) }

const tabs = computed(() => [
    { key: 'comments', label: `${t('boards.comments')} (${props.comments.filter(c => !c.deleted).length})` },
    { key: 'transitions', label: `${t('boards.transitions')} (${props.transitions.length + props.history.length})` },
    { key: 'all', label: t('boards.activityAll') },
])

function transitionActorName(tr: BoardTicketTransition): string {
    return tr.actorName ?? '—'
}

function historyActorName(entry: BoardTicketHistoryEntry): string {
    return entry.actorName ?? '—'
}

function laneName(id: number | null): string {
    if (id === null) return '—'
    return props.lanes.find(l => l.id === id)?.name ?? `#${id}`
}

function formatDate(iso: string): string {
    return new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' })
}

</script>

<template>
    <div>
        <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

        <!-- Comments (using CommentThread) -->
        <div v-if="activeTab === 'comments'">
            <CommentThread
                :comments="commentsAsGeneric"
                :members="members"
                :federated="federated"
                :readonly="readonly"
                @create="(parentId, content) => emit('createComment', parentId, content)"
                @update="(id, content) => emit('updateComment', id, content)"
                @delete="(id) => emit('deleteComment', id)"
            />
        </div>

        <!-- Transitions + History -->
        <div v-if="activeTab === 'transitions'" class="space-y-2">
            <div v-for="tr in transitions" :key="`t-${tr.id}`" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                <MemberName :identity="tr.actor" :name="transitionActorName(tr)" size="sm" />
                <span>{{ t('boards.movedFrom') }}</span>
                <span class="font-medium text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: lanes.find(l => l.id === tr.fromLaneId)?.color ?? 'var(--primary)', color: contrastTextColor(lanes.find(l => l.id === tr.fromLaneId)?.color ?? '#fd4f00') }">{{ laneName(tr.fromLaneId) }}</span>
                <span>{{ t('boards.movedTo') }}</span>
                <span class="font-medium text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: lanes.find(l => l.id === tr.toLaneId)?.color ?? 'var(--primary)', color: contrastTextColor(lanes.find(l => l.id === tr.toLaneId)?.color ?? '#fd4f00') }">{{ laneName(tr.toLaneId) }}</span>
                <span class="ml-auto text-xs">{{ formatDate(tr.movedAt) }}</span>
            </div>
            <template v-for="h in history" :key="`h-${h.id}`">
                <div class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="h.actor" :name="historyActorName(h)" size="sm" />
                    <span class="font-medium text-(--text)">{{ historyActionLabel[h.action] ?? h.action }}</span>
                    <!-- Priority: show icons -->
                    <template v-if="h.action === 'PRIORITY_CHANGED' && h.detail">
                        <template v-for="(part, i) in h.detail.split(' → ')" :key="i">
                            <span v-if="i > 0" class="text-(--text-muted)">→</span>
                            <font-awesome-icon v-if="priorityIcons[part]" :icon="priorityIcons[part].icon" :class="priorityIcons[part].color" />
                        </template>
                    </template>
                    <!-- Label: show colored pill -->
                    <template v-else-if="(h.action === 'LABEL_ADDED' || h.action === 'LABEL_REMOVED') && h.detail">
                        <span class="text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: findLabel(h.detail)?.color ?? '#6b7280', color: contrastTextColor(findLabel(h.detail)?.color ?? '#6b7280') }">{{ h.detail }}</span>
                    </template>
                    <!-- Due date -->
                    <template v-else-if="h.action === 'DUE_DATE_CHANGED' && h.detail">
                        <span class="text-(--text)">{{ h.detail }}</span>
                    </template>
                    <!-- Other: raw detail -->
                    <span v-else-if="h.detail" class="text-(--text)">{{ h.detail }}</span>
                    <span class="ml-auto text-xs">{{ formatDate(h.createdAt) }}</span>
                </div>
            </template>
            <p v-if="transitions.length === 0 && history.length === 0" class="text-sm text-(--text-muted) text-center py-4">—</p>
        </div>

        <!-- All (interleaved) -->
        <div v-if="activeTab === 'all'" class="space-y-3">
            <div v-for="item in allActivity" :key="`${item.type}-${item.data.id}`">
                <div v-if="item.type === 'comment'" class="flex gap-2">
                    <div class="flex-1">
                        <div class="flex items-center gap-2 text-xs text-(--text-muted)">
                            <MemberName :identity="(item.data as BoardComment).author" :name="(item.data as BoardComment).authorName" size="sm" class="font-medium" />
                            <span>{{ formatDate(item.ts) }}</span>
                        </div>
                        <p class="text-sm mt-0.5 whitespace-pre-wrap">{{ (item.data as BoardComment).content }}</p>
                    </div>
                </div>
                <div v-else-if="item.type === 'transition'" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="(item.data as BoardTicketTransition).actor" :name="transitionActorName(item.data as BoardTicketTransition)" size="sm" />
                    <span>{{ t('boards.movedFrom') }}</span>
                    <span class="font-medium text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: lanes.find(l => l.id === (item.data as BoardTicketTransition).fromLaneId)?.color ?? 'var(--primary)', color: contrastTextColor(lanes.find(l => l.id === (item.data as BoardTicketTransition).fromLaneId)?.color ?? '#fd4f00') }">{{ laneName((item.data as BoardTicketTransition).fromLaneId) }}</span>
                    <span>{{ t('boards.movedTo') }}</span>
                    <span class="font-medium text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: lanes.find(l => l.id === (item.data as BoardTicketTransition).toLaneId)?.color ?? 'var(--primary)', color: contrastTextColor(lanes.find(l => l.id === (item.data as BoardTicketTransition).toLaneId)?.color ?? '#fd4f00') }">{{ laneName((item.data as BoardTicketTransition).toLaneId) }}</span>
                    <span class="ml-auto text-xs">{{ formatDate(item.ts) }}</span>
                </div>
                <div v-else-if="item.type === 'history'" class="flex items-center gap-2 text-sm text-(--text-muted) flex-wrap">
                    <MemberName :identity="(item.data as BoardTicketHistoryEntry).actor" :name="historyActorName(item.data as BoardTicketHistoryEntry)" size="sm" />
                    <span class="font-medium text-(--text)">{{ historyActionLabel[(item.data as BoardTicketHistoryEntry).action] ?? (item.data as BoardTicketHistoryEntry).action }}</span>
                    <template v-if="(item.data as BoardTicketHistoryEntry).action === 'PRIORITY_CHANGED' && (item.data as BoardTicketHistoryEntry).detail">
                        <template v-for="(part, i) in ((item.data as BoardTicketHistoryEntry).detail ?? '').split(' → ')" :key="i">
                            <span v-if="i > 0" class="text-(--text-muted)">→</span>
                            <font-awesome-icon v-if="priorityIcons[part]" :icon="priorityIcons[part].icon" :class="priorityIcons[part].color" />
                        </template>
                    </template>
                    <template v-else-if="((item.data as BoardTicketHistoryEntry).action === 'LABEL_ADDED' || (item.data as BoardTicketHistoryEntry).action === 'LABEL_REMOVED') && (item.data as BoardTicketHistoryEntry).detail">
                        <span class="text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: findLabel((item.data as BoardTicketHistoryEntry).detail!)?.color ?? '#6b7280', color: contrastTextColor(findLabel((item.data as BoardTicketHistoryEntry).detail!)?.color ?? '#6b7280') }">{{ (item.data as BoardTicketHistoryEntry).detail }}</span>
                    </template>
                    <span v-else-if="(item.data as BoardTicketHistoryEntry).detail" class="text-(--text)">{{ (item.data as BoardTicketHistoryEntry).detail }}</span>
                    <span class="ml-auto text-xs">{{ formatDate(item.ts) }}</span>
                </div>
            </div>
            <p v-if="allActivity.length === 0" class="text-sm text-(--text-muted) text-center py-4">—</p>
        </div>

    </div>
</template>
