/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import UserTagBadge from '@/components/badge/UserTagBadge.vue'
import {getMemberPickerByUid, type MemberSearchResult} from '@/api/members'
import {resolveMemberListSource, type LayoutKindName, type MemberListConfig, type MemberSpotlightConfig, type ResolvedMember} from '@/api/pageManage'

/**
 * Renderer for MEMBER_SPOTLIGHT and MEMBER_LIST_SPOTLIGHT (member-list spotlight).
 *
 * <p>MEMBER_LIST_SPOTLIGHT uses {@code resolvedMembers} when present (the server-baked, public-safe
 * shape produced by {@code getPageRendered} — avatars are inlined as {@code data:} URLs so
 * unauthenticated visitors never hit the auth-gated avatar endpoint) and falls back to the
 * PAGE_EDIT-gated resolve endpoint for the editor preview.
 */
const props = defineProps<{
    kind: LayoutKindName
    config: Record<string, unknown>
}>()

function asCfg<T>(): T { return props.config as T }
const memberSpotlight = computed(() => asCfg<MemberSpotlightConfig>())
const memberList = computed(() => asCfg<MemberListConfig>())

const spotlightShowUserType = computed(() => memberSpotlight.value.showUserType !== false)
const memberListShowUserType = computed(() => memberList.value.showUserType !== false)

const memberResolved = ref<MemberSearchResult | null>(null)
async function resolveMemberSpotlight() {
    if (!memberSpotlight.value.memberUid) { memberResolved.value = null; return }
    try {
        memberResolved.value = await getMemberPickerByUid(memberSpotlight.value.memberUid)
    } catch { memberResolved.value = null }
}

const memberListResolved = ref<ResolvedMember[]>([])
async function resolveMemberList() {
    if (memberList.value.resolvedMembers && memberList.value.resolvedMembers.length >= 0) {
        memberListResolved.value = memberList.value.resolvedMembers
        return
    }
    const source = memberList.value.source
    if (!source) { memberListResolved.value = []; return }
    try {
        memberListResolved.value = await resolveMemberListSource(
            source,
            memberList.value.sortBy ?? null,
            memberList.value.memberDescriptions ?? null,
            memberList.value.memberOrder ?? null,
        )
    } catch { memberListResolved.value = [] }
}

onMounted(() => {
    if (props.kind === 'MEMBER_SPOTLIGHT') resolveMemberSpotlight()
    if (props.kind === 'MEMBER_LIST_SPOTLIGHT') resolveMemberList()
})
watch(() => memberSpotlight.value.memberUid, resolveMemberSpotlight, {immediate: false})
watch(() => JSON.stringify(memberList.value.source ?? null), resolveMemberList, {immediate: false})
watch(() => memberList.value.sortBy, resolveMemberList, {immediate: false})
watch(() => JSON.stringify(memberList.value.memberDescriptions ?? null), resolveMemberList, {immediate: false})
watch(() => JSON.stringify(memberList.value.memberOrder ?? null), resolveMemberList, {immediate: false})
watch(() => memberList.value.resolvedMembers, resolveMemberList, {immediate: false})
</script>

<template>
    <EmptyHint v-if="kind === 'MEMBER_SPOTLIGHT' && !memberSpotlight.memberUid">Kein Mitglied ausgewählt</EmptyHint>
    <div v-else-if="kind === 'MEMBER_SPOTLIGHT' && memberResolved"
         class="flex gap-3 items-start rounded-theme border border-(--border) p-4">
        <img v-if="memberResolved.avatarUrl" :src="memberResolved.avatarUrl" :alt="memberResolved.displayName"
             class="w-16 h-16 rounded-full object-cover shrink-0"/>
        <div v-else class="w-16 h-16 rounded-full bg-primary/15 text-primary flex items-center justify-center shrink-0">
            <font-awesome-icon :icon="['fas', 'user']" class="text-2xl"/>
        </div>
        <div class="flex-1 min-w-0">
            <p class="font-semibold flex items-center gap-2 flex-wrap">
                <span>{{ memberResolved.displayName }}</span>
                <UserTagBadge
                    v-if="memberSpotlight.showTag && memberResolved.displayTag"
                    :color="memberResolved.displayTagColor"
                >{{ memberResolved.displayTag }}</UserTagBadge>
            </p>
            <MutedText v-if="spotlightShowUserType && memberResolved.userType" tag="p">{{ memberResolved.userType }}</MutedText>
            <MutedText v-if="memberSpotlight.blurb" tag="p" size="sm" class="mt-1">{{ memberSpotlight.blurb }}</MutedText>
        </div>
    </div>
    <EmptyHint v-else-if="kind === 'MEMBER_SPOTLIGHT'">Mitglied nicht mehr verfügbar</EmptyHint>

    <div v-else-if="kind === 'MEMBER_LIST_SPOTLIGHT'" class="space-y-3">
        <p v-if="memberList.title" class="font-semibold">{{ memberList.title }}</p>
        <ul v-if="memberListResolved.length > 0"
            class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            <li v-for="m in memberListResolved" :key="m.memberUid"
                class="flex gap-3 items-center rounded-theme border border-(--border) p-3">
                <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.displayName"
                     class="w-12 h-12 rounded-full object-cover shrink-0"/>
                <div v-else class="w-12 h-12 rounded-full bg-primary/15 text-primary flex items-center justify-center shrink-0">
                    <font-awesome-icon :icon="['fas', 'user']"/>
                </div>
                <div class="min-w-0 flex-1">
                    <p class="font-medium flex items-center gap-2 flex-wrap min-w-0">
                        <span class="truncate">{{ m.displayName }}</span>
                        <UserTagBadge
                            v-if="memberList.showTag && m.displayTag"
                            :color="m.displayTagColor"
                            class="shrink-0"
                        >{{ m.displayTag }}</UserTagBadge>
                    </p>
                    <MutedText v-if="memberListShowUserType && m.userType" tag="p" class="truncate">{{ m.userType }}</MutedText>
                    <MutedText v-if="m.description" tag="p" size="sm" class="mt-1">{{ m.description }}</MutedText>
                </div>
            </li>
        </ul>
        <EmptyHint v-else>Keine Mitglieder ausgewählt</EmptyHint>
    </div>
</template>
