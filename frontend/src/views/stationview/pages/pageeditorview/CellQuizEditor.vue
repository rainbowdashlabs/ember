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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import * as publicQuiz from '@/api/publicQuiz'

/**
 * Editor block for the QUIZ_TEASER cell. The cell stores a list of public catalog ids — the
 * renderer draws one random question from them and reveals the answer on click.
 */
const props = defineProps<{
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

const catalogs = ref<publicQuiz.PublicQuizCatalog[]>([])
const catalogsLoaded = ref(false)
async function loadCatalogs() {
    if (catalogsLoaded.value || !props.stationUid) return
    try {
        catalogs.value = await publicQuiz.listPublicCatalogs(props.stationUid)
    } catch { catalogs.value = [] }
    finally { catalogsLoaded.value = true }
}
onMounted(loadCatalogs)
watch(() => props.stationUid, loadCatalogs)

function toggleCatalog(id: number) {
    const current = (props.config.catalogIds as number[] | undefined) ?? []
    const next = current.includes(id) ? current.filter(x => x !== id) : [...current, id]
    patch({catalogIds: next.length === 0 ? null : next})
}
function isSelected(id: number): boolean {
    return ((props.config.catalogIds as number[] | undefined) ?? []).includes(id)
}
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('quizTitle') }}</FieldLabel>
    <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('quizDescription') }}</FieldLabel>
    <MarkdownFieldInput :model-value="(config.description as string) ?? ''"
                        @update:model-value="patch({description: $event ?? ''})"/>
    <FieldLabel hint class="mb-1 mt-2">{{ TS('quizCatalogs') }}</FieldLabel>
    <p v-if="catalogsLoaded && catalogs.length === 0" class="text-xs text-(--text-muted) italic">
        {{ TS('quizCatalogsEmpty') }}
    </p>
    <div v-else class="flex flex-wrap gap-2">
        <SelectionToggleButton v-for="c in catalogs" :key="c.id"
                               :selected="isSelected(c.id)"
                               @toggle="toggleCatalog(c.id)">
            {{ c.name }}
        </SelectionToggleButton>
    </div>
    <p v-if="((config.catalogIds as number[] | undefined) ?? []).length === 0"
       class="text-xs text-(--text-muted) mt-1">{{ TS('quizCatalogsHint') }}</p>
</template>
