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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { FormQuestionRequest, QuestionType, Role, MemberGroup, UserTag } from '@/api/types'
import { Roles, QuestionTypes } from '@/api/types'
import { forms, stationMembers, memberGroups, userTags } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const formId = computed(() => route.params.id ? Number(route.params.id) : null)

const loading = ref(false)
const error = ref('')

// Form metadata
const title = ref('')
const description = ref('')
const shuffleQuestions = ref(false)
const allowEdit = ref(true)
const startAt = ref('')
const endAt = ref('')

// Restrictions
const allRoles = ref<Role[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const selectedRoleIds = ref<Set<number>>(new Set())
const selectedGroupIds = ref<Set<number>>(new Set())
const selectedTagIds = ref<Set<number>>(new Set())

const RESTRICTION_ROLES = [Roles.MEMBER, Roles.GUARDIAN, Roles.TEAM] as readonly string[]
const roleFriendlyNames: Record<string, string> = {
  [Roles.MEMBER]: 'Mitglied',
  [Roles.GUARDIAN]: 'Erziehungsberechtigter',
  [Roles.TEAM]: 'Team',
}
const restrictionRoles = computed(() =>
    allRoles.value.filter(r => RESTRICTION_ROLES.includes(r.role))
)

function toggleRole(roleId: number) {
  const s = new Set(selectedRoleIds.value)
  if (s.has(roleId)) s.delete(roleId); else s.add(roleId)
  selectedRoleIds.value = s
}

function toggleGroup(groupId: number) {
  const s = new Set(selectedGroupIds.value)
  if (s.has(groupId)) s.delete(groupId); else s.add(groupId)
  selectedGroupIds.value = s
}

function toggleTag(tagId: number) {
  const s = new Set(selectedTagIds.value)
  if (s.has(tagId)) s.delete(tagId); else s.add(tagId)
  selectedTagIds.value = s
}

// Questions
interface QuestionDraft {
  id: string
  questionType: QuestionType
  title: string
  description: string
  required: boolean
  shuffle: boolean
  config: Record<string, unknown>
}

const questions = ref<QuestionDraft[]>([])
let nextTempId = 1

const questionTypes: QuestionType[] = [QuestionTypes.CHOICE, QuestionTypes.TEXT, QuestionTypes.RATING, QuestionTypes.DATE, QuestionTypes.RANKING, QuestionTypes.LIKERT]

function addQuestion(type: QuestionType) {
  const defaultConfig = getDefaultConfig(type)
  questions.value.push({
    id: `temp-${nextTempId++}`,
    questionType: type,
    title: '',
    description: '',
    required: false,
    shuffle: false,
    config: defaultConfig,
  })
}

function getDefaultConfig(type: QuestionType): Record<string, unknown> {
  switch (type) {
    case QuestionTypes.CHOICE: return { multiSelect: false, dropdown: false, allowOther: false, options: [''], multiLimitType: 'NONE', multiLimit: null }
    case QuestionTypes.TEXT: return { longAnswer: false }
    case QuestionTypes.RATING: return { scale: 5, icon: 'STAR' }
    case QuestionTypes.DATE: return {}
    case QuestionTypes.RANKING: return { options: [''] }
    case QuestionTypes.LIKERT: return { statements: [''], scaleMin: 1, scaleMax: 5, scaleLabels: [] }
  }
}

function removeQuestion(index: number) {
  questions.value.splice(index, 1)
}

function moveQuestion(index: number, direction: -1 | 1) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= questions.value.length) return
  const temp = questions.value[index]
  questions.value[index] = questions.value[newIndex]
  questions.value[newIndex] = temp
}

function setMultiSelect(q: QuestionDraft, val: boolean) {
  q.config.multiSelect = val
  if (val) q.config.dropdown = false
}

function setDropdown(q: QuestionDraft, val: boolean) {
  q.config.dropdown = val
  if (val) {
    q.config.multiSelect = false
    q.config.multiLimitType = 'NONE'
    q.config.multiLimit = null
  }
}

// Option helpers (shared for CHOICE and RANKING)
function addOption(q: QuestionDraft) {
  const opts = (q.config.options as string[]) || []
  opts.push('')
  q.config.options = opts
}

function removeOption(q: QuestionDraft, index: number) {
  const opts = (q.config.options as string[]) || []
  opts.splice(index, 1)
  q.config.options = opts
}

function updateOption(q: QuestionDraft, index: number, value: string) {
  const opts = [...((q.config.options as string[]) || [])]
  opts[index] = value
  q.config.options = opts
}

function moveOption(q: QuestionDraft, index: number, direction: -1 | 1) {
  const opts = (q.config.options as string[]) || []
  const newIdx = index + direction
  if (newIdx < 0 || newIdx >= opts.length) return
  const temp = opts[index]
  opts[index] = opts[newIdx]
  opts[newIdx] = temp
  q.config.options = [...opts]
}

// Likert helpers
function addStatement(q: QuestionDraft) {
  const stmts = (q.config.statements as string[]) || []
  stmts.push('')
  q.config.statements = stmts
}

function removeStatement(q: QuestionDraft, index: number) {
  const stmts = (q.config.statements as string[]) || []
  stmts.splice(index, 1)
  q.config.statements = stmts
}

function updateStatement(q: QuestionDraft, index: number, value: string) {
  const stmts = [...((q.config.statements as string[]) || [])]
  stmts[index] = value
  q.config.statements = stmts
}

function moveStatement(q: QuestionDraft, index: number, direction: -1 | 1) {
  const stmts = (q.config.statements as string[]) || []
  const newIdx = index + direction
  if (newIdx < 0 || newIdx >= stmts.length) return
  const temp = stmts[index]
  stmts[index] = stmts[newIdx]
  stmts[newIdx] = temp
  q.config.statements = [...stmts]
}

async function loadForm() {
  loading.value = true
  try {
    const [roles, groups, tags] = await Promise.all([
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
      userTags.listTags(),
    ])
    allRoles.value = roles
    allGroups.value = groups
    allTags.value = tags

    if (!formId.value) { loading.value = false; return }

    const [form, qs, restrictions] = await Promise.all([
      forms.getForm(formId.value),
      forms.getQuestions(formId.value),
      forms.getRestrictions(formId.value),
    ])
    title.value = form.title
    description.value = form.description
    shuffleQuestions.value = form.shuffleQuestions
    allowEdit.value = form.allowEdit
    startAt.value = form.startAt ? form.startAt.slice(0, 16) : ''
    endAt.value = form.endAt ? form.endAt.slice(0, 16) : ''

    selectedRoleIds.value = new Set(restrictions.roleIds)
    selectedGroupIds.value = new Set(restrictions.groupIds)
    selectedTagIds.value = new Set(restrictions.tagIds)

    questions.value = qs.map(q => ({
      id: `existing-${q.id}`,
      questionType: q.questionType,
      title: q.title,
      description: q.description,
      required: q.required,
      shuffle: q.shuffle,
      config: JSON.parse(q.config || '{}'),
    }))
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    let id = formId.value
    const formData = {
      title: title.value,
      description: description.value,
      shuffleQuestions: shuffleQuestions.value,
      allowEdit: allowEdit.value,
      startAt: startAt.value ? new Date(startAt.value).toISOString() : null,
      endAt: endAt.value ? new Date(endAt.value).toISOString() : null,
    }

    if (id) {
      await forms.updateForm(id, formData)
    } else {
      const created = await forms.createForm(formData)
      id = created.id
    }

    const questionRequests: FormQuestionRequest[] = questions.value.map(q => ({
      questionType: q.questionType,
      title: q.title,
      description: q.description,
      required: q.required,
      shuffle: q.shuffle,
      config: JSON.stringify(q.config),
    }))
    await forms.setQuestions(id!, questionRequests)

    await forms.setRestrictions(id!, {
      roleIds: [...selectedRoleIds.value],
      groupIds: [...selectedGroupIds.value],
      tagIds: [...selectedTagIds.value],
    })

    router.push({ name: 'forms-list' })
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadForm)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- Form Metadata -->
        <NeutralContainer>
          <div class="space-y-4">
            <TextInput v-model="title" :placeholder="t('forms.title')" />
            <TextAreaInput v-model="description" :placeholder="t('forms.description')" />
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="text-xs text-(--text-muted) block mb-1">{{ t('forms.startAt') }}</label>
                <input v-model="startAt" type="datetime-local"
                       class="w-full px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:border-primary focus:outline-none text-sm" />
              </div>
              <div>
                <label class="text-xs text-(--text-muted) block mb-1">{{ t('forms.endAt') }}</label>
                <input v-model="endAt" type="datetime-local"
                       class="w-full px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:border-primary focus:outline-none text-sm" />
              </div>
            </div>
            <div class="flex gap-6">
              <label class="flex items-center gap-2 text-sm">
                <ToggleInput v-model="shuffleQuestions" />
                {{ t('forms.shuffleQuestions') }}
              </label>
              <label class="flex items-center gap-2 text-sm">
                <ToggleInput v-model="allowEdit" />
                {{ t('forms.allowEdit') }}
              </label>
            </div>
          </div>
        </NeutralContainer>

        <!-- Restrictions -->
        <NeutralContainer>
          <div class="space-y-4">
            <SubHeader>{{ t('forms.restrictions.title') }}</SubHeader>
            <div class="space-y-2">
              <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.roles') }}</label>
              <div class="flex flex-wrap gap-2">
                <button v-for="role in restrictionRoles" :key="role.id"
                        :class="selectedRoleIds.has(role.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                        class="px-2 py-1 text-xs rounded border transition-colors"
                        @click="toggleRole(role.id)">
                  {{ roleFriendlyNames[role.role] ?? role.role }}
                </button>
              </div>
            </div>
            <div class="space-y-2">
              <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.groups') }}</label>
              <div class="flex flex-wrap gap-2">
                <button v-for="group in allGroups" :key="group.id"
                        :class="selectedGroupIds.has(group.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                        class="px-2 py-1 text-xs rounded border transition-colors"
                        @click="toggleGroup(group.id)">
                  {{ group.name }}
                </button>
                <span v-if="allGroups.length === 0" class="text-xs text-(--text-muted)">–</span>
              </div>
            </div>
            <div class="space-y-2">
              <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.tags') }}</label>
              <div class="flex flex-wrap gap-2">
                <button v-for="tag in allTags" :key="tag.id"
                        :class="selectedTagIds.has(tag.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                        class="px-2 py-1 text-xs rounded border transition-colors"
                        @click="toggleTag(tag.id)">
                  {{ tag.name }}
                </button>
                <span v-if="allTags.length === 0" class="text-xs text-(--text-muted)">–</span>
              </div>
            </div>
            <p v-if="selectedRoleIds.size === 0 && selectedGroupIds.size === 0 && selectedTagIds.size === 0"
               class="text-xs text-(--text-muted) italic">{{ t('forms.restrictions.noRestrictions') }}</p>
          </div>
        </NeutralContainer>

        <!-- Questions -->
        <div class="space-y-3">
          <NeutralContainer v-for="(q, idx) in questions" :key="q.id">
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <span class="text-xs font-semibold text-(--text-muted) uppercase">
                  {{ idx + 1 }}. {{ t(`forms.questionTypes.${q.questionType}`) }}
                </span>
                <div class="flex gap-1">
                  <IconButton :icon="['fas', 'chevron-up']" :label="'Up'" :disabled="idx === 0" class="text-(--text-muted) hover:text-primary" @click="moveQuestion(idx, -1)" />
                  <IconButton :icon="['fas', 'chevron-down']" :label="'Down'" :disabled="idx === questions.length - 1" class="text-(--text-muted) hover:text-primary" @click="moveQuestion(idx, 1)" />
                  <DeleteButton @click="removeQuestion(idx)" />
                </div>
              </div>

              <TextInput v-model="q.title" :placeholder="t('forms.questionTitle')" />
              <TextInput v-model="q.description" :placeholder="t('forms.questionDescription')" />

              <div class="flex gap-4">
                <label class="flex items-center gap-2 text-sm">
                  <ToggleInput v-model="q.required" />
                  {{ t('forms.questionRequired') }}
                </label>
                <label v-if="q.questionType === QuestionTypes.CHOICE || q.questionType === QuestionTypes.RANKING || q.questionType === QuestionTypes.LIKERT"
                       class="flex items-center gap-2 text-sm">
                  <ToggleInput v-model="q.shuffle" />
                  {{ t('forms.questionShuffle') }}
                </label>
              </div>

              <!-- CHOICE -->
              <template v-if="q.questionType === QuestionTypes.CHOICE">
                <div class="flex gap-4 flex-wrap">
                  <label class="flex items-center gap-2 text-sm">
                    <ToggleInput :model-value="!!q.config.multiSelect" @update:model-value="setMultiSelect(q, $event)" />
                    {{ t('forms.choice.multiSelect') }}
                  </label>
                  <label class="flex items-center gap-2 text-sm">
                    <ToggleInput :model-value="!!q.config.dropdown" @update:model-value="setDropdown(q, $event)" />
                    {{ t('forms.choice.dropdown') }}
                  </label>
                  <label class="flex items-center gap-2 text-sm">
                    <ToggleInput v-model="(q.config.allowOther as boolean)" />
                    {{ t('forms.choice.allowOther') }}
                  </label>
                </div>
                <div v-if="q.config.multiSelect" class="flex gap-4 items-center">
                  <SelectInput v-model="(q.config.multiLimitType as string)" class="w-40">
                    <option value="NONE">{{ t('forms.choice.limitNone') }}</option>
                    <option value="EQUAL_TO">{{ t('forms.choice.limitEqual') }}</option>
                    <option value="AT_MOST">{{ t('forms.choice.limitAtMost') }}</option>
                    <option value="AT_LEAST">{{ t('forms.choice.limitAtLeast') }}</option>
                  </SelectInput>
                  <NumberInput v-if="q.config.multiLimitType !== 'NONE'" v-model="(q.config.multiLimit as number)" :placeholder="t('forms.choice.limitValue')" class="w-24" />
                </div>
                <div class="space-y-1">
                  <label class="text-xs text-(--text-muted)">{{ t('forms.choice.options') }}</label>
                  <div v-for="(opt, oi) in (q.config.options as string[])" :key="oi" class="flex gap-2 items-center">
                    <TextInput :model-value="opt" class="flex-1" @update:model-value="(v: string | undefined) => updateOption(q, oi, v ?? '')" />
                    <IconButton :icon="['fas', 'chevron-up']" :label="'Up'" class="text-(--text-muted) hover:text-primary" @click="moveOption(q, oi, -1)" />
                    <IconButton :icon="['fas', 'chevron-down']" :label="'Down'" class="text-(--text-muted) hover:text-primary" @click="moveOption(q, oi, 1)" />
                    <DeleteButton @click="removeOption(q, oi)" />
                  </div>
                  <SecondaryButton class="text-xs" @click="addOption(q)">{{ t('forms.choice.addOption') }}</SecondaryButton>
                </div>
              </template>

              <!-- TEXT -->
              <template v-if="q.questionType === QuestionTypes.TEXT">
                <label class="flex items-center gap-2 text-sm">
                  <ToggleInput v-model="(q.config.longAnswer as boolean)" />
                  {{ t('forms.text.longAnswer') }}
                </label>
              </template>

              <!-- RATING -->
              <template v-if="q.questionType === QuestionTypes.RATING">
                <div class="flex gap-4 items-center">
                  <label class="text-sm">{{ t('forms.rating.scale') }}</label>
                  <NumberInput v-model="(q.config.scale as number)" class="w-20" />
                  <SelectInput v-model="(q.config.icon as string)" class="w-40">
                    <option value="STAR">{{ t('forms.rating.iconStar') }}</option>
                    <option value="NUMBER">{{ t('forms.rating.iconNumber') }}</option>
                    <option value="HEART">{{ t('forms.rating.iconHeart') }}</option>
                    <option value="THUMB_UP">{{ t('forms.rating.iconThumbUp') }}</option>
                  </SelectInput>
                </div>
              </template>

              <!-- RANKING -->
              <template v-if="q.questionType === QuestionTypes.RANKING">
                <div class="space-y-1">
                  <label class="text-xs text-(--text-muted)">{{ t('forms.ranking.options') }}</label>
                  <div v-for="(opt, oi) in (q.config.options as string[])" :key="oi" class="flex gap-2 items-center">
                    <TextInput :model-value="opt" class="flex-1" @update:model-value="(v: string | undefined) => updateOption(q, oi, v ?? '')" />
                    <IconButton :icon="['fas', 'chevron-up']" :label="'Up'" class="text-(--text-muted) hover:text-primary" @click="moveOption(q, oi, -1)" />
                    <IconButton :icon="['fas', 'chevron-down']" :label="'Down'" class="text-(--text-muted) hover:text-primary" @click="moveOption(q, oi, 1)" />
                    <DeleteButton @click="removeOption(q, oi)" />
                  </div>
                  <SecondaryButton class="text-xs" @click="addOption(q)">{{ t('forms.ranking.addOption') }}</SecondaryButton>
                </div>
              </template>

              <!-- LIKERT -->
              <template v-if="q.questionType === QuestionTypes.LIKERT">
                <div class="flex gap-4 items-center">
                  <label class="text-sm">{{ t('forms.likert.scaleMin') }}</label>
                  <NumberInput v-model="(q.config.scaleMin as number)" class="w-20" />
                  <label class="text-sm">{{ t('forms.likert.scaleMax') }}</label>
                  <NumberInput v-model="(q.config.scaleMax as number)" class="w-20" />
                </div>
                <div class="space-y-1">
                  <label class="text-xs text-(--text-muted)">{{ t('forms.likert.statements') }}</label>
                  <div v-for="(stmt, si) in (q.config.statements as string[])" :key="si" class="flex gap-2 items-center">
                    <TextInput :model-value="stmt" class="flex-1" @update:model-value="(v: string | undefined) => updateStatement(q, si, v ?? '')" />
                    <IconButton :icon="['fas', 'chevron-up']" :label="'Up'" class="text-(--text-muted) hover:text-primary" @click="moveStatement(q, si, -1)" />
                    <IconButton :icon="['fas', 'chevron-down']" :label="'Down'" class="text-(--text-muted) hover:text-primary" @click="moveStatement(q, si, 1)" />
                    <DeleteButton @click="removeStatement(q, si)" />
                  </div>
                  <SecondaryButton class="text-xs" @click="addStatement(q)">{{ t('forms.likert.addStatement') }}</SecondaryButton>
                </div>
              </template>
            </div>
          </NeutralContainer>
        </div>

        <!-- Add Question -->
        <div class="flex flex-wrap gap-2">
          <SecondaryButton v-for="type in questionTypes" :key="type" class="text-xs" @click="addQuestion(type)">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t(`forms.questionTypes.${type}`) }}
          </SecondaryButton>
        </div>

        <!-- Actions -->
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'forms-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="save">{{ t('common.save') }}</PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
