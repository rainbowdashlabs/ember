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
import MemberSearchPicker from '@/components/input/search/MemberSearchPicker.vue'
import MemberListDynamicList from './MemberListDynamicList.vue'
import MemberListManualList from './MemberListManualList.vue'
import MemberListStaticList from './MemberListStaticList.vue'
import {listGroups} from '@/api/memberGroups'
import {listTags} from '@/api/userTags'
import {getMemberPickerByUid, type MemberSearchResult} from '@/api/members'
import {MemberListSortBy, resolveMemberListSource, type MemberListSortByName, type MemberListSource, type ResolvedMember} from '@/api/pageManage'
import type {MemberGroup, UserTag} from '@/api/types'
import {useConfigPatch} from '@/composables/useConfigPatch'

const config = defineModel<Record<string, unknown>>('config', {required: true})

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const cfg = computed(() => config.value)
const patch = useConfigPatch(cfg, (_event, value) => { config.value = value })

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
    missing.forEach((uid, i) => {
        nextAttempted[uid] = true
        const resolved = results[i]
        if (resolved) nextNames[uid] = resolved.displayName
    })
    memberNames.value = nextNames
    memberResolveAttempted.value = nextAttempted
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

function toMemberListSource(src: OfficersSource): MemberListSource | null {
    if (src.kind === 'manual') return {kind: 'manual', memberUids: src.memberUids}
    if (src.kind === 'group') return {kind: 'group', groupId: src.groupId}
    if (src.kind === 'tag') return {kind: 'tag', tagId: src.tagId}
    return null
}

async function refreshDynamicResolved() {
    const src = toMemberListSource(source.value)
    const hasTarget = src !== null && (
        (src.kind === 'manual' && (src.memberUids?.length ?? 0) > 0)
        || (src.kind === 'group' && !!src.groupId)
        || (src.kind === 'tag' && !!src.tagId))
    if (!src || !hasTarget) { dynamicResolved.value = []; return }
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
    if (item === undefined) return
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
        @update:model-value="(v: string | number | null | undefined) => patch({source: {kind: String(v ?? '')}})">
        <option v-for="k in (['group','tag','manual'] as const)" :key="k" :value="k">{{ TS('memberListSource' + k.charAt(0).toUpperCase() + k.slice(1)) }}</option>
    </SelectInput>

    <template v-if="sourceKind === 'group'">
        <FieldLabel hint class="mb-1">{{ TS('memberListGroupPick') }}</FieldLabel>
        <SelectInput :model-value="String(source.groupId ?? '')" class="w-full"
            @update:model-value="(v: string | number | null | undefined) => patch({source: {kind: 'group', groupId: v ? Number(v) : null}})">
            <option value="">{{ TS('memberListGroupPickEmpty') }}</option>
            <option v-for="g in groupsList" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
        </SelectInput>
    </template>

    <template v-else-if="sourceKind === 'tag'">
        <FieldLabel hint class="mb-1">{{ TS('memberListTagPick') }}</FieldLabel>
        <SelectInput :model-value="String(source.tagId ?? '')" class="w-full"
            @update:model-value="(v: string | number | null | undefined) => patch({source: {kind: 'tag', tagId: v ? Number(v) : null}})">
            <option value="">{{ TS('memberListTagPickEmpty') }}</option>
            <option v-for="ut in tagsList" :key="ut.id" :value="String(ut.id)">{{ ut.name }}</option>
        </SelectInput>
    </template>

    <template v-if="(sourceKind === 'group' || sourceKind === 'tag') && dynamicResolved.length > 0">
        <FieldLabel hint class="mb-1 mt-2">{{ TS('memberListGroupTagDescriptionsTitle') }}</FieldLabel>
        <p class="text-xs text-(--text-muted) mb-1">{{ TS('memberListGroupTagDescriptionsHint') }}</p>
        <MemberListDynamicList
            :members="dynamicResolved"
            :is-order-sort="isOrderSort"
            :description-for="descriptionFor"
            @move="moveDynamicMember"
            @set-description="setDescription"
        />
    </template>

    <template v-else-if="sourceKind === 'manual'">
        <FieldLabel hint class="mb-1">{{ TS('memberListManualList') }}</FieldLabel>
        <MemberListManualList
            v-if="isOrderSort"
            :uids="manualUids"
            :member-names="memberNames"
            :member-resolve-attempted="memberResolveAttempted"
            :description-for="descriptionFor"
            @move="moveManualMember"
            @remove="removeManualMember"
            @set-description="setDescription"
        />
        <MemberListStaticList
            v-else
            :members="dynamicResolved"
            :description-for="descriptionFor"
            @remove="removeManualMember"
            @set-description="setDescription"
        />
        <MemberSearchPicker
            :model-value="null"
            @pick="(item: MemberSearchResult) => addManualMember(item.memberUid)"
        />
    </template>

    <FieldLabel hint class="mb-1">{{ TS('sortBy') }}</FieldLabel>
    <SelectInput :model-value="(cfg.sortBy as string) ?? MemberListSortBy.ORDER" class="w-full"
        @update:model-value="(v: string | number | null | undefined) => patch({sortBy: (v == null ? MemberListSortBy.ORDER : String(v)) as MemberListSortByName})">
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
