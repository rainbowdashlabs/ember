/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { QuizCatalog, QuizCategory, QuizSectionDetail, PermissionGrant, MemberGroup, UserTag } from '@/api/types'
import { quiz, stationMembers, memberGroups, userTags } from '@/api'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const testId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => testId.value !== null)

const loading = ref(false)
const error = ref('')

// Form fields
const title = ref('')
const description = ref('')
const timeLimit = ref<number | undefined>(undefined)
const timeLimitEnabled = ref(false)
const shuffle = ref(false)
const startAt = ref('')
const endAt = ref('')

// Restrictions
const allRoles = ref<PermissionGrant[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const selectedRoleIds = ref<number[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])

// Catalogs for source selection
const catalogs = ref<QuizCatalog[]>([])
const catalogCategories = ref<Map<number, QuizCategory[]>>(new Map())

// Sections
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [catalogRes, roles, groups, tags] = await Promise.all([
      quiz.listCatalogs(),
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
      userTags.listTags(),
    ])
    catalogs.value = Array.isArray(catalogRes) ? catalogRes as unknown as QuizCatalog[] : (catalogRes.catalogs ?? [])
    allRoles.value = roles
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
      startAt.value = test.startAt ? test.startAt.slice(0, 16) : ''
      endAt.value = test.endAt ? test.endAt.slice(0, 16) : ''

      // Load categories for all catalogs used in sections
      const catalogIds = new Set<number>()
      for (const sec of detail.sections) {
        for (const src of sec.sources) {
          catalogIds.add(src.catalogId)
        }
      }
      await Promise.all([...catalogIds].map(id => loadCatalogCategories(id)))

      // Load restrictions
      try {
        const restrictions = await quiz.getRestrictions(testId.value)
        selectedRoleIds.value = restrictions.roleIds ?? []
        selectedGroupIds.value = restrictions.groupIds ?? []
        selectedTagIds.value = restrictions.tagIds ?? []
      } catch { /* no restrictions */ }

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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    const testData = {
      title: title.value,
      description: description.value,
      timeLimit: timeLimitEnabled.value && timeLimit.value ? timeLimit.value : null,
      shuffle: shuffle.value,
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

    // Save restrictions
    await quiz.setRestrictions(id!, {
      roleIds: selectedRoleIds.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
    })

    router.push({ name: 'quiz-test-detail', params: { id: id! } })
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <SectionHeader>{{ isEdit ? t('quiz.tests.editTest') : t('quiz.tests.createTest') }}</SectionHeader>

        <!-- Test metadata -->
        <NeutralContainer>
          <div class="space-y-4">
            <TextInput v-model="title" :placeholder="t('quiz.tests.titlePlaceholder')" />
            <TextAreaInput v-model="description" :placeholder="t('quiz.tests.descriptionPlaceholder')" />

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <FieldLabel hint class="mb-1">{{ t('quiz.tests.startAt') }}</FieldLabel>
                <DateTimeInput v-model="startAt" />
              </div>
              <div>
                <FieldLabel hint class="mb-1">{{ t('quiz.tests.endAt') }}</FieldLabel>
                <DateTimeInput v-model="endAt" />
              </div>
            </div>

            <div class="flex flex-wrap gap-6">
              <FieldLabel inline>
                <ToggleInput v-model="shuffle" />
                {{ t('quiz.tests.shuffle') }}
              </FieldLabel>
              <FieldLabel inline>
                <ToggleInput v-model="timeLimitEnabled" />
                {{ t('quiz.tests.timeLimitEnabled') }}
              </FieldLabel>
            </div>

            <div v-if="timeLimitEnabled" class="flex items-center gap-2">
              <label class="text-sm text-(--text-muted)">{{ t('quiz.tests.timeLimitMinutes') }}</label>
              <NumberInput v-model="timeLimit" class="w-24" />
            </div>
          </div>
        </NeutralContainer>

        <!-- Restrictions -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('quiz.tests.restrictions') }}</SubHeader>
          <RestrictionPicker
              :roles="allRoles"
              :groups="allGroups"
              :tags="allTags"
              :selected-role-ids="selectedRoleIds"
              :selected-group-ids="selectedGroupIds"
              :selected-tag-ids="selectedTagIds"
              @update:selected-role-ids="v => selectedRoleIds = v"
              @update:selected-group-ids="v => selectedGroupIds = v"
              @update:selected-tag-ids="v => selectedTagIds = v"
          />
        </NeutralContainer>

        <!-- Sections -->
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('quiz.sections.title') }}</SectionHeader>
            <SecondaryButton :icon="['fas', 'plus']" @click="addSection">
              {{ t('quiz.sections.add') }}
            </SecondaryButton>
          </div>

          <EmptyState compact v-if="sections.length === 0">{{ t('quiz.sections.noSections') }}</EmptyState>

          <NeutralContainer v-for="(section, sIdx) in sections" :key="section.key">
            <div class="space-y-4">
              <div class="flex items-center justify-between">
                <SectionLabel>
                  {{ t('quiz.sections.sectionNumber', { n: sIdx + 1 }) }}
                </SectionLabel>
                <div class="flex gap-1">
                  <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')" :disabled="sIdx === 0"
                              class="text-(--text-muted) hover:text-primary" @click="moveSection(sIdx, -1)" />
                  <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')" :disabled="sIdx === sections.length - 1"
                              class="text-(--text-muted) hover:text-primary" @click="moveSection(sIdx, 1)" />
                  <DeleteButton @click="removeSection(sIdx)" />
                </div>
              </div>

              <TextInput v-model="section.title" :placeholder="t('quiz.sections.titlePlaceholder')" />
              <TextAreaInput v-model="section.description" :placeholder="t('quiz.sections.descriptionPlaceholder')" />

              <!-- Sources -->
              <div class="space-y-3">
                <label class="text-xs text-(--text-muted) font-medium">{{ t('quiz.sections.sources') }}</label>

                <div v-for="(source, srcIdx) in section.sources" :key="source.key"
                     class="flex flex-col sm:flex-row gap-2 items-start sm:items-center p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
                  <SelectInput
                    :model-value="String(source.catalogId ?? '')"
                    class="flex-1"
                    @update:model-value="(v: string | undefined) => onCatalogChange(source, v)"
                  >
                    <option value="">{{ t('quiz.sections.selectCatalog') }}</option>
                    <option v-for="catalog in catalogs" :key="catalog.id" :value="String(catalog.id)">
                      {{ catalog.name }}
                    </option>
                  </SelectInput>

                  <SelectInput
                    v-if="getCategoriesForCatalog(source.catalogId).length > 0"
                    :model-value="String(source.categoryId ?? '')"
                    class="flex-1"
                    @update:model-value="(v: string | undefined) => source.categoryId = v ? Number(v) : null"
                  >
                    <option value="">{{ t('quiz.sections.allCategories') }}</option>
                    <option v-for="cat in getCategoriesForCatalog(source.catalogId)" :key="cat.id" :value="String(cat.id)">
                      {{ cat.name }}
                    </option>
                  </SelectInput>

                  <div class="flex items-center gap-2">
                    <label class="text-xs text-(--text-muted) whitespace-nowrap">{{ t('quiz.sections.questionCount') }}</label>
                    <NumberInput v-model="source.questionCount" class="w-20" />
                  </div>

                  <DeleteButton @click="removeSource(section, srcIdx)" />
                </div>

                <SecondaryButton :icon="['fas', 'plus']" @click="addSource(section)">
                  {{ t('quiz.sections.addSource') }}
                </SecondaryButton>
              </div>
            </div>
          </NeutralContainer>
        </div>

        <!-- Actions -->
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'quiz-tests' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="save">{{ t('common.save') }}</PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
