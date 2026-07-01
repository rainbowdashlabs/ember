/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { QuizCatalog, QuizCategory, QuizSectionDetail, MemberGroup, UserTag } from '@/api/types'
import { quiz, memberGroups, userTags } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import TestMetadataForm from './testbuilderview/TestMetadataForm.vue'
import TestRestrictionsForm from './testbuilderview/TestRestrictionsForm.vue'
import TestSectionsEditor from './testbuilderview/TestSectionsEditor.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const testId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => testId.value !== null)
const routeKey = computed(() => isEdit.value ? 'quiz-test-edit' : 'quiz-test-create')

const title = ref('')
const description = ref('')
const timeLimit = ref<number | undefined>(undefined)
const timeLimitEnabled = ref(false)
const shuffle = ref(false)
const forced = ref(false)
const startAt = ref('')
const endAt = ref('')

const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])

const catalogs = ref<QuizCatalog[]>([])
const catalogCategories = ref<Map<number, QuizCategory[]>>(new Map())

interface SourceDraft {
  key: string
  catalogId: number | null
  categoryId: number | null
  questionCount: number
}

interface SectionDraft {
  key: string
  title: string
  description: string
  sources: SourceDraft[]
}

const sections = ref<SectionDraft[]>([])
let nextKey = 1

function generateKey(): string {
  return `key-${nextKey++}`
}

function addSection() {
  sections.value.push({
    key: generateKey(),
    title: '',
    description: '',
    sources: [],
  })
}

function removeSection(index: number) {
  sections.value.splice(index, 1)
}

function moveSection(index: number, direction: -1 | 1) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= sections.value.length) return
  const temp = sections.value[index]
  sections.value[index] = sections.value[newIndex]
  sections.value[newIndex] = temp
}

function addSource(section: SectionDraft) {
  section.sources.push({
    key: generateKey(),
    catalogId: null,
    categoryId: null,
    questionCount: 5,
  })
}

function removeSource(section: SectionDraft, sourceIndex: number) {
  section.sources.splice(sourceIndex, 1)
}

async function loadCatalogCategories(catalogId: number) {
  if (catalogCategories.value.has(catalogId)) return
  try {
    const detail = await quiz.getCatalog(catalogId)
    catalogCategories.value.set(catalogId, detail.categories)
  } catch {
    catalogCategories.value.set(catalogId, [])
  }
}

function onCatalogChange(source: SourceDraft, value: string | undefined) {
  const id = value ? Number(value) : null
  source.catalogId = id
  source.categoryId = null
  if (id) loadCatalogCategories(id)
}

function getCategoriesForCatalog(catalogId: number | null): QuizCategory[] {
  if (!catalogId) return []
  return catalogCategories.value.get(catalogId) ?? []
}

const { loading, error } = useAsyncLoader(async () => {
  const [catalogRes, groups, tags] = await Promise.all([
    quiz.listCatalogs(),
    memberGroups.listGroups(),
    userTags.listTags(),
  ])
  catalogs.value = Array.isArray(catalogRes) ? catalogRes as unknown as QuizCatalog[] : (catalogRes.catalogs ?? [])
  allGroups.value = groups
  allTags.value = tags

  if (testId.value) {
    const detail = await quiz.getTest(testId.value)
    const test = detail.test
    title.value = test.title
    description.value = test.description
    timeLimit.value = test.timeLimit ?? undefined
    timeLimitEnabled.value = test.timeLimit !== null
    shuffle.value = test.shuffle
    forced.value = test.forced ?? false
    startAt.value = test.startAt ? test.startAt.slice(0, 16) : ''
    endAt.value = test.endAt ? test.endAt.slice(0, 16) : ''

    const catalogIds = new Set<number>()
    for (const sec of detail.sections) {
      for (const src of sec.sources) {
        catalogIds.add(src.catalogId)
      }
    }
    await Promise.all([...catalogIds].map(id => loadCatalogCategories(id)))

    try {
      const restrictions = await quiz.getRestrictions(testId.value)
      selectedUserTypes.value = restrictions.userTypes ?? []
      selectedGroupIds.value = restrictions.groupIds ?? []
      selectedTagIds.value = restrictions.tagIds ?? []
    } catch { void 0 }

    sections.value = detail.sections.map((sec: QuizSectionDetail) => ({
      key: generateKey(),
      title: sec.title,
      description: sec.description,
      sources: sec.sources.map(src => ({
        key: generateKey(),
        catalogId: src.catalogId,
        categoryId: src.categoryId,
        questionCount: src.questionCount,
      })),
    }))
  }
})

async function save() {
  error.value = ''
  try {
    const testData = {
      title: title.value,
      description: description.value,
      timeLimit: timeLimitEnabled.value && timeLimit.value ? timeLimit.value : null,
      shuffle: shuffle.value,
      forced: forced.value,
      startAt: startAt.value ? new Date(startAt.value).toISOString() : null,
      endAt: endAt.value ? new Date(endAt.value).toISOString() : null,
    }

    let id = testId.value
    if (id) {
      await quiz.updateTest(id, testData)
    } else {
      const created = await quiz.createTest(testData)
      id = created.id
    }

    const sectionPayload = sections.value.map(sec => ({
      title: sec.title,
      description: sec.description,
      sources: sec.sources
        .filter(src => src.catalogId !== null)
        .map(src => ({
          catalogId: src.catalogId!,
          categoryId: src.categoryId,
          questionCount: src.questionCount,
        })),
    }))

    await quiz.replaceSections(id!, sectionPayload)

    await quiz.setRestrictions(id!, {
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
    })

    router.push({ name: 'quiz-test-detail', params: { id: id! } })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <ViewContent :title="t(`pages.${routeKey}.title`)" :subtitle="t(`pages.${routeKey}.subtitle`)">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <TestMetadataForm
            v-model:title="title"
            v-model:description="description"
            v-model:start-at="startAt"
            v-model:end-at="endAt"
            v-model:shuffle="shuffle"
            v-model:forced="forced"
            v-model:time-limit-enabled="timeLimitEnabled"
            v-model:time-limit="timeLimit"
        />

        <TestRestrictionsForm
            :groups="allGroups"
            :tags="allTags"
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="selectedGroupIds"
            v-model:selected-tag-ids="selectedTagIds"
        />

        <TestSectionsEditor
            :sections="sections"
            :catalogs="catalogs"
            :get-categories-for-catalog="getCategoriesForCatalog"
            :on-catalog-change="onCatalogChange"
            @add-section="addSection"
            @move-section="moveSection"
            @remove-section="removeSection"
            @add-source="addSource"
            @remove-source="removeSource"
        />

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'quiz-tests' })">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :action="save"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
