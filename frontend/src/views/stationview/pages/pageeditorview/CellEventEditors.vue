/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventSearchPicker from '@/components/input/search/EventSearchPicker.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import * as publicEvents from '@/api/publicEvents'
import * as events from '@/api/events'
import type {EventCategory} from '@/api/types'

/**
 * Editor block for FEATURED_EVENT / UPCOMING_EVENTS / PAST_EVENT_RECAP cells. Extracted from
 * {@code CellLayoutEditors.vue} purely to keep that file under the 500-line view limit; logic
 * is unchanged.
 */
const props = defineProps<{
    kind: 'FEATURED_EVENT' | 'UPCOMING_EVENTS' | 'PAST_EVENT_RECAP'
    config: Record<string, unknown>
    stationUid: string
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}

const eventCategories = ref<EventCategory[]>([])
const eventCategoriesLoaded = ref(false)
async function ensureEventCategoriesLoaded() {
    if (eventCategoriesLoaded.value) return
    try {
        eventCategories.value = await events.listCategories()
    } catch { eventCategories.value = [] }
    finally { eventCategoriesLoaded.value = true }
}
onMounted(() => { if (props.kind === 'UPCOMING_EVENTS') ensureEventCategoriesLoaded() })
watch(() => props.kind, k => { if (k === 'UPCOMING_EVENTS') ensureEventCategoriesLoaded() })

function toggleCategoryId(id: number) {
    const current = (props.config.categoryIds as number[] | undefined) ?? []
    const next = current.includes(id) ? current.filter(x => x !== id) : [...current, id]
    patch({categoryIds: next.length === 0 ? null : next})
}
function isCategorySelected(id: number): boolean {
    return ((props.config.categoryIds as number[] | undefined) ?? []).includes(id)
}

/** Pre-fills descriptionOverride from the picked event's own description when still empty. */
async function onFeaturedEventPick(eventUid: string) {
    const updates: Record<string, unknown> = {eventUid}
    if (!props.config.descriptionOverride && props.stationUid) {
        try {
            const list = await publicEvents.listPublicEvents(props.stationUid)
            const match = list.find(e => e.publicUid === eventUid)
            if (match?.description) updates.descriptionOverride = match.description
        } catch { /* leave override empty on lookup failure */ }
    }
    patch(updates)
}
</script>

<template>
    <!-- FEATURED_EVENT — pick one public event; title/date/location/CTA all come live from it. -->
    <template v-if="kind === 'FEATURED_EVENT'">
        <FieldLabel hint class="mb-1">{{ TS('chooseFeaturedEvent') }}</FieldLabel>
        <EventSearchPicker
            :model-value="(config.eventUid as string) ?? null"
            mode="ALL"
            :station-uid="stationUid"
            @pick="(item: {eventUid: string}) => onFeaturedEventPick(item.eventUid)"
            @update:model-value="(v: string | null) => patch({eventUid: v})"
        />
        <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
        <MarkdownFieldInput
            :model-value="(config.descriptionOverride as string) ?? ''"
            @update:model-value="patch({descriptionOverride: $event ?? ''})"
        />
    </template>

    <!-- UPCOMING_EVENTS — live list filtered by category. -->
    <template v-else-if="kind === 'UPCOMING_EVENTS'">
        <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
        <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
                <FieldLabel hint class="mb-1">{{ TS('eventLimit') }}</FieldLabel>
                <NumberInput
                    :model-value="(config.limit as number) ?? 5"
                    @update:model-value="patch({limit: Math.max(1, Math.min(20, $event || 5))})"
                />
            </div>
            <div class="flex items-end gap-2 pb-1">
                <ToggleInput
                    :model-value="config.includeFederated !== false"
                    @update:model-value="patch({includeFederated: $event})"
                />
                <FieldLabel hint class="mb-0">{{ TS('includeFederated') }}</FieldLabel>
            </div>
        </div>
        <FieldLabel hint class="mb-1 mt-2">{{ TS('eventCategories') }}</FieldLabel>
        <p v-if="eventCategoriesLoaded && eventCategories.length === 0" class="text-xs text-(--text-muted) italic">
            {{ TS('eventCategoriesEmpty') }}
        </p>
        <div v-else class="flex flex-wrap gap-2">
            <SelectionToggleButton
                v-for="c in eventCategories" :key="c.id"
                :selected="isCategorySelected(c.id)"
                @toggle="toggleCategoryId(c.id)"
            >{{ c.name }}</SelectionToggleButton>
        </div>
        <p v-if="((config.categoryIds as number[] | undefined) ?? []).length === 0"
           class="text-xs text-(--text-muted) mt-1">{{ TS('eventCategoriesAll') }}</p>
    </template>

    <!-- PAST_EVENT_RECAP — pick a past event; title/date/image come live, recap text is editor-supplied. -->
    <template v-else-if="kind === 'PAST_EVENT_RECAP'">
        <FieldLabel hint class="mb-1">{{ TS('choosePastEvent') }}</FieldLabel>
        <EventSearchPicker
            :model-value="(config.eventUid as string) ?? null"
            mode="PAST"
            :station-uid="stationUid"
            @pick="(item: {eventUid: string}) => patch({eventUid: item.eventUid})"
            @update:model-value="(v: string | null) => patch({eventUid: v})"
        />
        <FieldLabel hint class="mb-1">{{ TS('eventDescription') }}</FieldLabel>
        <MarkdownFieldInput
            :model-value="(config.recapDescription as string) ?? ''"
            @update:model-value="patch({recapDescription: $event ?? ''})"
        />
    </template>
</template>
