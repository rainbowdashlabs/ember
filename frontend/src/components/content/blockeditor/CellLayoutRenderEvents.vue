/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import * as publicEvents from '@/api/publicEvents'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {
    FeaturedEventConfig,
    LayoutKindName,
    PastEventRecapConfig,
    UpcomingEventsConfig,
} from '@/api/pageManage'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
    kind: LayoutKindName
    config: Record<string, unknown>
    stationUid?: string
}>()

function asCfg<T>(): T { return props.config as T }

const featuredEvent = computed(() => asCfg<FeaturedEventConfig>())
const pastEvent = computed(() => asCfg<PastEventRecapConfig>())
const upcomingEvents = computed(() => asCfg<UpcomingEventsConfig>())

interface FeaturedEventResolved {
    name: string
    description: string
    startTime: string | null
    categoryName: string | null
    href: string
}

interface PastEventResolved {
    name: string
    startTime: string | null
    href: string
}

interface UpcomingEventResolved {
    name: string
    startTime: string | null
    categoryName: string | null
    href: string
}

const featuredResolved = ref<FeaturedEventResolved | null>(null)
const pastEventResolved = ref<PastEventResolved | null>(null)
const upcomingResolved = ref<UpcomingEventResolved[]>([])

async function resolveFeaturedEvent() {
    if (!props.stationUid || !featuredEvent.value.eventUid) return
    try {
        const events = await publicEvents.listPublicEvents(props.stationUid)
        const match = events.find(e => e.publicUid === featuredEvent.value.eventUid)
        if (match) {
            featuredResolved.value = {
                name: match.name,
                description: match.description ?? '',
                startTime: match.startTime ?? null,
                categoryName: match.categoryName ?? null,
                href: `/public/station/${props.stationUid}/events/${match.publicUid}`,
            }
        }
    } catch {
        return
    }
}

async function resolvePastEvent() {
    if (!props.stationUid || !pastEvent.value.eventUid) return
    try {
        const events = await publicEvents.listPublicEvents(props.stationUid)
        const match = events.find(e => e.publicUid === pastEvent.value.eventUid)
        if (match) {
            pastEventResolved.value = {
                name: match.name,
                startTime: match.startTime ?? null,
                href: `/public/station/${props.stationUid}/events/${match.publicUid}`,
            }
        }
    } catch {
        return
    }
}

async function resolveUpcomingEvents() {
    if (!props.stationUid) return
    try {
        const events = await publicEvents.listPublicEvents(props.stationUid)
        const limit = Math.max(1, Math.min(20, upcomingEvents.value.limit ?? 5))
        const now = Date.now()
        const cats = upcomingEvents.value.categoryIds ?? null
        upcomingResolved.value = events
            .filter(e => e.startTime && new Date(e.startTime).getTime() >= now)
            .filter(e => !cats || cats.length === 0 || (e.categoryId != null && cats.includes(e.categoryId)))
            .slice(0, limit)
            .map(e => ({
                name: e.name,
                startTime: e.startTime ?? null,
                categoryName: e.categoryName ?? null,
                href: `/public/station/${props.stationUid}/events/${e.publicUid}`,
            }))
    } catch {
        return
    }
}

onMounted(() => {
    if (props.kind === 'FEATURED_EVENT') resolveFeaturedEvent()
    if (props.kind === 'PAST_EVENT_RECAP') resolvePastEvent()
    if (props.kind === 'UPCOMING_EVENTS') resolveUpcomingEvents()
})

watch(() => [props.stationUid, featuredEvent.value.eventUid], resolveFeaturedEvent, {immediate: false})
watch(() => [props.stationUid, pastEvent.value.eventUid], resolvePastEvent, {immediate: false})
watch(
    () => [props.stationUid, upcomingEvents.value.limit, JSON.stringify(upcomingEvents.value.categoryIds ?? null)],
    resolveUpcomingEvents,
    {immediate: false},
)

function formatLongDateTime(iso: string): string {
    return new Date(iso).toLocaleString('de-DE', {dateStyle: 'long', timeStyle: 'short'})
}
</script>

<template>
    <div v-if="kind === 'FEATURED_EVENT' && featuredResolved" class="rounded-theme border border-primary/40 bg-primary/5 p-4 space-y-2">
        <div class="flex items-start gap-3">
            <font-awesome-icon :icon="['fas', 'calendar-days']" class="text-2xl text-primary mt-1"/>
            <div class="flex-1 min-w-0">
                <p class="font-semibold text-base">{{ featuredResolved.name }}</p>
                <p v-if="featuredResolved.startTime" class="text-sm text-(--text-muted)">{{ formatLongDateTime(featuredResolved.startTime) }}</p>
                <p v-if="featuredResolved.categoryName" class="text-xs text-(--text-muted)">{{ featuredResolved.categoryName }}</p>
            </div>
        </div>
        <p v-if="featuredEvent.descriptionOverride" class="text-sm whitespace-pre-line">{{ featuredEvent.descriptionOverride }}</p>
        <p v-else-if="featuredResolved.description" class="text-sm whitespace-pre-line">{{ featuredResolved.description }}</p>
        <a :href="featuredResolved.href" class="inline-block px-3 py-1.5 rounded-theme bg-primary !text-primary-text text-sm font-medium hover:bg-primary-accent">Mehr erfahren</a>
    </div>
    <EmptyHint v-else-if="kind === 'FEATURED_EVENT'">Nicht mehr verfügbar</EmptyHint>

    <div v-else-if="kind === 'UPCOMING_EVENTS'" class="space-y-2">
        <p v-if="upcomingEvents.title" class="font-semibold">{{ upcomingEvents.title }}</p>
        <ul class="space-y-2">
            <li v-for="(item, i) in upcomingResolved" :key="i" class="flex items-start gap-3 rounded-theme border border-(--border) px-3 py-2">
                <font-awesome-icon :icon="['fas', 'calendar']" class="text-primary mt-0.5"/>
                <div class="flex-1 min-w-0">
                    <a :href="item.href" class="font-medium hover:text-primary hover:underline">{{ item.name }}</a>
                    <p class="text-xs text-(--text-muted)">{{ [item.startTime ? formatDateTime(item.startTime) : '', item.categoryName].filter(Boolean).join(' · ') }}</p>
                </div>
            </li>
            <li v-if="upcomingResolved.length === 0" class="text-xs text-(--text-muted) italic">Keine bevorstehenden Termine.</li>
        </ul>
    </div>

    <div v-else-if="kind === 'PAST_EVENT_RECAP' && pastEventResolved" class="rounded-theme border border-(--border) overflow-hidden">
        <div class="p-3 space-y-1">
            <p class="font-semibold">
                <a :href="pastEventResolved.href" class="hover:text-primary hover:underline">{{ pastEventResolved.name }}</a>
            </p>
            <p v-if="pastEventResolved.startTime" class="text-xs text-(--text-muted)">{{ formatLongDateTime(pastEventResolved.startTime) }}</p>
            <p v-if="pastEvent.recapDescription" class="text-sm whitespace-pre-line">{{ pastEvent.recapDescription }}</p>
        </div>
    </div>
    <EmptyHint v-else-if="kind === 'PAST_EVENT_RECAP'">Nicht mehr verfügbar</EmptyHint>
</template>
