/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {FeaturedEventConfig} from '@/api/pageManage'
import {useEventRoutes} from '@/composables/useEventRoutes'
import {formatDateTimeLong} from '@/util/format'
import {occurrenceLabel} from '@/util/occurrenceLabel'
import {findEmbeddedEvent, publicEventsAddress, type FoundEvent} from '../embeddedEventLookup'

/**
 * One event, shown live from the event itself.
 *
 * <p>The block keeps only the event's public id, the occurrence it is about and an optional text of
 * its own, so moving the event moves the block with it. Where the reader cannot see the event, as on
 * a partner station or the public blog for an event the station keeps to itself, the block says so
 * and shows nothing else about it.
 */
const props = defineProps<{
    config: FeaturedEventConfig
    stationUid?: string
    /** The clock an appointment is written on, the reader's own where none is named. */
    timezone?: string | null
}>()

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()

const found = ref<FoundEvent | null>(null)
const looked = ref(false)

async function resolve() {
    found.value = null
    looked.value = false
    if (props.stationUid && props.config.eventUid) {
        found.value = await findEmbeddedEvent(props.stationUid, props.config.eventUid)
    }
    looked.value = true
}

onMounted(resolve)
watch(() => [props.stationUid, props.config.eventUid], resolve)

const when = computed(() => {
    const event = found.value
    if (!event) return ''
    if (props.config.date) {
        return occurrenceLabel(props.config.date, event.startTime, event.endTime, t('stationPages.cells.eventUntil'), props.timezone)
    }
    return event.startTime ? formatDateTimeLong(event.startTime, props.timezone) : ''
})

const description = computed(() => props.config.descriptionOverride || found.value?.description || '')

const href = computed(() => {
    const source = found.value?.source
    if (!source) return ''
    if (source.kind === 'PUBLIC') return publicEventsAddress(source.stationUid)
    return props.config.date
        ? router.resolve({name: eventRoutes.detailOnDate, params: {id: source.eventId, date: props.config.date}}).href
        : router.resolve({name: eventRoutes.detail, params: {id: source.eventId}}).href
})
</script>

<template>
    <div v-if="found" class="rounded-theme border border-primary/40 bg-primary/5 p-4 space-y-2" data-testid="featured-event">
        <div class="flex items-start gap-3">
            <font-awesome-icon :icon="['fas', 'calendar-days']" class="text-2xl text-primary mt-1"/>
            <div class="flex-1 min-w-0">
                <p class="font-semibold text-base">
                    {{ found.name }}
                    <ErrorBadge v-if="found.cancelled" class="ml-2">{{ t('stationPages.cells.eventCancelled') }}</ErrorBadge>
                </p>
                <p v-if="when" class="text-sm text-(--text-muted)">{{ when }}</p>
                <p v-if="found.categoryName" class="text-xs text-(--text-muted)">{{ found.categoryName }}</p>
            </div>
        </div>
        <p v-if="description" class="text-sm whitespace-pre-line">{{ description }}</p>
        <a :href="href" class="inline-block px-3 py-1.5 rounded-theme bg-primary !text-primary-text text-sm font-medium hover:bg-primary-accent">
            {{ t('stationPages.cells.eventMore') }}
        </a>
    </div>
    <EmptyHint v-else-if="looked">{{ t('stationPages.cells.eventUnavailable') }}</EmptyHint>
</template>
