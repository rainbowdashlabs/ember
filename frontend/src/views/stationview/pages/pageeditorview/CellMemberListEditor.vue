/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberSearchPicker from '@/components/input/search/MemberSearchPicker.vue'
import {listGroups} from '@/api/memberGroups'
import {listTags} from '@/api/userTags'
import {getMemberPickerByUid, type MemberSearchResult} from '@/api/members'
import {MemberListSortBy, resolveMemberListSource, type MemberListSortByName, type ResolvedMember} from '@/api/pageManage'
import type {MemberGroup, UserTag} from '@/api/types'

const props = defineProps<{
    config: Record<string, unknown>
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const cfg = computed(() => props.config)
function patch(partial: Record<string, unknown>) {
    emit('update:config', {...cfg.value, ...partial})
}

type OfficersSource = {kind?: string; groupId?: number | null; tagId?: number | null; memberUids?: string[]}
const source = computed<OfficersSource>(() => (cfg.value.source as OfficersSource) ?? {})
const sourceKind = computed(() => source.value.kind ?? 'group')

const groupsList = ref<MemberGroup[]>([])
const tagsList = ref<UserTag[]>([])
const groupsLoaded = ref(false)
const tagsLoaded = ref(false)
const memberNames = ref<Record<string, string>>({})
const memberResolveAttempted = ref<Record<string, boolean>>({})

async function ensureGroupsLoaded() {
    if (groupsLoaded.value) return
    groupsLoaded.value = true
    try { groupsList.value = await listGroups() } catch { groupsList.value = [] }
}
async function ensureTagsLoaded() {
    if (tagsLoaded.value) return
    tagsLoaded.value = true
    try { tagsList.value = await listTags() } catch { tagsList.value = [] }
}

async function resolveNames(uids: string[]) {
    const missing = uids.filter(uid => !(uid in memberResolveAttempted.value))
    if (missing.length === 0) return
    const results = await Promise.all(missing.map(uid => getMemberPickerByUid(uid).catch(() => null)))
    const nextNames = {...memberNames.value}
    const nextAttempted = {...memberResolveAttempted.value}
    results.forEach((r, i) => {
        nextAttempted[missing[i]] = true
        if (r) nextNames[missing[i]] = r.displayName
    })
    memberNames.value = nextNames
    memberResolveAttempted.value = nextAttempted
}

function memberLabel(uid: string): string {
    if (memberNames.value[uid]) return memberNames.value[uid]
    if (memberResolveAttempted.value[uid]) return t('stationPages.editor.memberListMissingMember')
    return uid
}

function descriptionFor(uid: string): string {
    return (cfg.value.memberDescriptions as Record<string, string> | undefined)?.[uid] ?? ''
}

function setDescription(uid: string, value: string | undefined) {
    const map = {...((cfg.value.memberDescriptions as Record<string, string> | undefined) ?? {})}
    if (value && value.trim().length > 0) {
        map[uid] = value
    } else {
        delete map[uid]
    }
    patch({memberDescriptions: Object.keys(map).length > 0 ? map : null})
}

watch(sourceKind, kind => {
    if (kind === 'group') ensureGroupsLoaded()
    if (kind === 'tag') ensureTagsLoaded()
}, {immediate: true})

const dynamicResolved = ref<ResolvedMember[]>([])
const manualUids = computed(() => sourceKind.value === 'manual' ? (source.value.memberUids ?? []) : [])
const isOrderSort = computed(() => ((cfg.value.sortBy as string | undefined) ?? MemberListSortBy.ORDER) === MemberListSortBy.ORDER)

async function refreshDynamicResolved() {
    const src = source.value
    const hasTarget = (src.kind === 'manual' && (src.memberUids?.length ?? 0) > 0)
        || (src.kind === 'group' && src.groupId)
        || (src.kind === 'tag' && src.tagId)
    if (!hasTarget) { dynamicResolved.value = []; return }
    try {
        dynamicResolved.value = await resolveMemberListSource(
            src,
            (cfg.value.sortBy as MemberListSortByName | undefined) ?? null,
            (cfg.value.memberDescriptions as Record<string, string> | undefined) ?? null,
            (cfg.value.memberOrder as string[] | undefined) ?? null,
        )
    } catch { dynamicResolved.value = [] }
}
watch(
    () => [
        JSON.stringify(source.value),
        cfg.value.sortBy,
        JSON.stringify(cfg.value.memberOrder ?? null),
    ],
    refreshDynamicResolved,
    {immediate: true},
)
watch(manualUids, uids => resolveNames(uids), {immediate: true})

function addManualMember(uid: string) {
    if (manualUids.value.includes(uid)) return
    patch({source: {kind: 'manual', memberUids: [...manualUids.value, uid]}})
}
function moveManualMember(from: number, to: number) {
    if (from === to || from < 0 || to < 0 || from >= manualUids.value.length || to >= manualUids.value.length) return
    const next = [...manualUids.value]
    const [item] = next.splice(from, 1)
    next.splice(to, 0, item)
    patch({source: {kind: 'manual', memberUids: next}, memberOrder: next})
}

function moveDynamicMember(from: number, to: number) {
    const list = dynamicResolved.value
    if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return
    const next = [...list]
    const item = next[from]!
    next.splice(from, 1)
    next.splice(to, 0, item)
    dynamicResolved.value = next
    patch({memberOrder: next.map(m => m.memberUid)})
}

function onDynamicDragStart(event: DragEvent, index: number) {
    dragIndex.value = index
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(index))
    }
}
function onDynamicDragOver(event: DragEvent, index: number) {
    if (dragIndex.value === null) return
    event.preventDefault()
    dragOverIndex.value = index
}
function onDynamicDrop(event: DragEvent, index: number) {
    event.preventDefault()
    if (dragIndex.value !== null) moveDynamicMember(dragIndex.value, index)
    dragIndex.value = null
    dragOverIndex.value = null
}

const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function onDragStart(event: DragEvent, index: number) {
    dragIndex.value = index
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(index))
    }
}
function onDragOver(event: DragEvent, index: number) {
    if (dragIndex.value === null) return
    event.preventDefault()
    dragOverIndex.value = index
}
function onDrop(event: DragEvent, index: number) {
    event.preventDefault()
    if (dragIndex.value !== null) moveManualMember(dragIndex.value, index)
    dragIndex.value = null
    dragOverIndex.value = null
}
function onDragEnd() {
    dragIndex.value = null
    dragOverIndex.value = null
}

function removeManualMember(uid: string) {
    const map = {...((cfg.value.memberDescriptions as Record<string, string> | undefined) ?? {})}
    delete map[uid]
    patch({
        source: {kind: 'manual', memberUids: manualUids.value.filter(id => id !== uid)},
        memberDescriptions: Object.keys(map).length > 0 ? map : null,
    })
}
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
    <TextInput :model-value="(cfg.title as string) ?? ''" @update:model-value="patch({title: $event})"/>

    <FieldLabel hint class="mb-1">{{ TS('memberListSource') }}</FieldLabel>
    <SelectInput :model-value="sourceKind" class="w-full"
        @update:model-value="(v: string) => patch({source: {kind: v}})">
        <option v-for="k in (['group','tag','manual'] as const)" :key="k" :value="k">{{ TS('memberListSource' + k.charAt(0).toUpperCase() + k.slice(1)) }}</option>
    </SelectInput>

    <template v-if="sourceKind === 'group'">
        <FieldLabel hint class="mb-1">{{ TS('memberListGroupPick') }}</FieldLabel>
        <SelectInput :model-value="String(source.groupId ?? '')" class="w-full"
            @update:model-value="(v: string) => patch({source: {kind: 'group', groupId: v ? Number(v) : null}})">
            <option value="">{{ TS('memberListGroupPickEmpty') }}</option>
            <option v-for="g in groupsList" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
        </SelectInput>
    </template>

    <template v-else-if="sourceKind === 'tag'">
        <FieldLabel hint class="mb-1">{{ TS('memberListTagPick') }}</FieldLabel>
        <SelectInput :model-value="String(source.tagId ?? '')" class="w-full"
            @update:model-value="(v: string) => patch({source: {kind: 'tag', tagId: v ? Number(v) : null}})">
            <option value="">{{ TS('memberListTagPickEmpty') }}</option>
            <option v-for="ut in tagsList" :key="ut.id" :value="String(ut.id)">{{ ut.name }}</option>
        </SelectInput>
    </template>

    <template v-if="(sourceKind === 'group' || sourceKind === 'tag') && dynamicResolved.length > 0">
        <FieldLabel hint class="mb-1 mt-2">{{ TS('memberListGroupTagDescriptionsTitle') }}</FieldLabel>
        <p class="text-xs text-(--text-muted) mb-1">{{ TS('memberListGroupTagDescriptionsHint') }}</p>
        <ul class="space-y-2 mb-2 text-sm">
            <li
                v-for="(m, i) in dynamicResolved"
                :key="m.memberUid"
                :draggable="isOrderSort"
                class="flex items-stretch gap-2 px-2 py-2 rounded-theme border border-(--border) transition-colors"
                :class="[
                    isOrderSort ? 'cursor-move' : '',
                    isOrderSort && dragOverIndex === i && dragIndex !== i ? 'border-primary bg-primary/5' : '',
                ]"
                @dragstart="isOrderSort ? onDynamicDragStart($event, i) : undefined"
                @dragover="isOrderSort ? onDynamicDragOver($event, i) : undefined"
                @drop="isOrderSort ? onDynamicDrop($event, i) : undefined"
                @dragend="onDragEnd"
            >
                <div v-if="isOrderSort" class="flex flex-col items-center justify-between shrink-0">
                    <IconButton
                        :icon="['fas', 'arrow-up']"
                        :label="TS('memberListManualMoveUp')"
                        :disabled="i === 0"
                        class="text-(--text-muted) hover:text-primary p-0.5!"
                        @click="moveDynamicMember(i, i - 1)"
                    />
                    <font-awesome-icon
                        :icon="['fas', 'grip-vertical']"
                        class="text-(--text-muted)"
                        :title="TS('memberListManualDragHandle')"
                    />
                    <IconButton
                        :icon="['fas', 'arrow-down']"
                        :label="TS('memberListManualMoveDown')"
                        :disabled="i === dynamicResolved.length - 1"
                        class="text-(--text-muted) hover:text-primary p-0.5!"
                        @click="moveDynamicMember(i, i + 1)"
                    />
                </div>
                <div class="flex-1 min-w-0 space-y-2">
                    <div class="flex items-center gap-2">
                        <font-awesome-icon :icon="['fas', 'user']" class="text-primary shrink-0"/>
                        <span class="flex-1 truncate" :title="m.memberUid">{{ m.displayName }}</span>
                    </div>
                    <TextInput
                        :model-value="descriptionFor(m.memberUid)"
                        :placeholder="TS('memberListDescriptionPlaceholder')"
                        @update:model-value="(v: string | undefined) => setDescription(m.memberUid, v)"
                    />
                </div>
            </li>
        </ul>
    </template>

    <template v-else-if="sourceKind === 'manual'">
        <FieldLabel hint class="mb-1">{{ TS('memberListManualList') }}</FieldLabel>
        <ul v-if="isOrderSort" class="space-y-2 mb-2 text-sm">
            <li
                v-for="(uid, i) in manualUids"
                :key="uid"
                :draggable="true"
                class="flex items-stretch gap-2 px-2 py-2 rounded-theme border border-(--border) cursor-move transition-colors"
                :class="dragOverIndex === i && dragIndex !== i ? 'border-primary bg-primary/5' : ''"
                @dragstart="onDragStart($event, i)"
                @dragover="onDragOver($event, i)"
                @drop="onDrop($event, i)"
                @dragend="onDragEnd"
            >
                <div class="flex flex-col items-center justify-between shrink-0">
                    <IconButton
                        :icon="['fas', 'arrow-up']"
                        :label="TS('memberListManualMoveUp')"
                        :disabled="i === 0"
                        class="text-(--text-muted) hover:text-primary p-0.5!"
                        @click="moveManualMember(i, i - 1)"
                    />
                    <font-awesome-icon
                        :icon="['fas', 'grip-vertical']"
                        class="text-(--text-muted)"
                        :title="TS('memberListManualDragHandle')"
                    />
                    <IconButton
                        :icon="['fas', 'arrow-down']"
                        :label="TS('memberListManualMoveDown')"
                        :disabled="i === manualUids.length - 1"
                        class="text-(--text-muted) hover:text-primary p-0.5!"
                        @click="moveManualMember(i, i + 1)"
                    />
                </div>
                <div class="flex-1 min-w-0 space-y-2">
                    <div class="flex items-center gap-2">
                        <font-awesome-icon :icon="['fas', 'user']" class="text-primary shrink-0"/>
                        <span
                            class="flex-1 truncate"
                            :title="uid"
                            :class="memberResolveAttempted[uid] && !memberNames[uid] ? 'italic text-(--text-muted)' : ''"
                        >{{ memberLabel(uid) }}</span>
                        <IconButton
                            :icon="['fas', 'xmark']"
                            :label="TS('memberListManualRemove')"
                            class="text-(--text-muted) hover:text-error p-1!"
                            @click="removeManualMember(uid)"
                        />
                    </div>
                    <TextInput
                        :model-value="descriptionFor(uid)"
                        :placeholder="TS('memberListDescriptionPlaceholder')"
                        @update:model-value="(v: string | undefined) => setDescription(uid, v)"
                    />
                </div>
            </li>
        </ul>
        <ul v-else class="space-y-2 mb-2 text-sm">
            <li
                v-for="m in dynamicResolved"
                :key="m.memberUid"
                class="flex items-stretch gap-2 px-2 py-2 rounded-theme border border-(--border)"
            >
                <div class="flex-1 min-w-0 space-y-2">
                    <div class="flex items-center gap-2">
                        <font-awesome-icon :icon="['fas', 'user']" class="text-primary shrink-0"/>
                        <span class="flex-1 truncate" :title="m.memberUid">{{ m.displayName }}</span>
                        <IconButton
                            :icon="['fas', 'xmark']"
                            :label="TS('memberListManualRemove')"
                            class="text-(--text-muted) hover:text-error p-1!"
                            @click="removeManualMember(m.memberUid)"
                        />
                    </div>
                    <TextInput
                        :model-value="descriptionFor(m.memberUid)"
                        :placeholder="TS('memberListDescriptionPlaceholder')"
                        @update:model-value="(v: string | undefined) => setDescription(m.memberUid, v)"
                    />
                </div>
            </li>
        </ul>
        <MemberSearchPicker
            :model-value="null"
            @pick="(item: MemberSearchResult) => addManualMember(item.memberUid)"
        />
    </template>

    <FieldLabel hint class="mb-1">{{ TS('sortBy') }}</FieldLabel>
    <SelectInput :model-value="(cfg.sortBy as string) ?? MemberListSortBy.ORDER" class="w-full"
        @update:model-value="(v: string | undefined) => patch({sortBy: (v ?? MemberListSortBy.ORDER) as MemberListSortByName})">
        <option :value="MemberListSortBy.ORDER">{{ TS('sortByOrder') }}</option>
        <option :value="MemberListSortBy.NAME">{{ TS('sortByName') }}</option>
        <option :value="MemberListSortBy.ROLE">{{ TS('sortByRole') }}</option>
        <option :value="MemberListSortBy.JOIN_DATE">{{ TS('sortByJoinDate') }}</option>
    </SelectInput>

    <div class="flex items-end gap-2 pt-1">
        <ToggleInput
            :model-value="cfg.showUserType !== false"
            @update:model-value="patch({showUserType: $event})"
        />
        <FieldLabel hint class="mb-0">{{ TS('showUserType') }}</FieldLabel>
    </div>
    <div class="flex items-end gap-2">
        <ToggleInput
            :model-value="!!cfg.showTag"
            @update:model-value="patch({showTag: $event})"
        />
        <FieldLabel hint class="mb-0">{{ TS('showTag') }}</FieldLabel>
    </div>
</template>
