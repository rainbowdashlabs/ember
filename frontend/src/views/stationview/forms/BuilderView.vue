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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import QuestionEditor from './builderview/QuestionEditor.vue'
import type { QuestionDraft } from './builderview/types'
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
                <DateTimeInput v-model="startAt" />
              </div>
              <div>
                <label class="text-xs text-(--text-muted) block mb-1">{{ t('forms.endAt') }}</label>
                <DateTimeInput v-model="endAt" />
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
                <SelectionToggleButton v-for="role in restrictionRoles" :key="role.id"
                        :selected="selectedRoleIds.has(role.id)"
                        @toggle="toggleRole(role.id)">
                  {{ roleFriendlyNames[role.role] ?? role.role }}
                </SelectionToggleButton>
              </div>
            </div>
            <div class="space-y-2">
              <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.groups') }}</label>
              <div class="flex flex-wrap gap-2">
                <SelectionToggleButton v-for="group in allGroups" :key="group.id"
                        :selected="selectedGroupIds.has(group.id)"
                        @toggle="toggleGroup(group.id)">
                  {{ group.name }}
                </SelectionToggleButton>
                <span v-if="allGroups.length === 0" class="text-xs text-(--text-muted)">--</span>
              </div>
            </div>
            <div class="space-y-2">
              <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.tags') }}</label>
              <div class="flex flex-wrap gap-2">
                <SelectionToggleButton v-for="tag in allTags" :key="tag.id"
                        :selected="selectedTagIds.has(tag.id)"
                        @toggle="toggleTag(tag.id)">
                  {{ tag.name }}
                </SelectionToggleButton>
                <span v-if="allTags.length === 0" class="text-xs text-(--text-muted)">--</span>
              </div>
            </div>
            <p v-if="selectedRoleIds.size === 0 && selectedGroupIds.size === 0 && selectedTagIds.size === 0"
               class="text-xs text-(--text-muted) italic">{{ t('forms.restrictions.noRestrictions') }}</p>
          </div>
        </NeutralContainer>

        <!-- Questions -->
        <div class="space-y-3">
          <QuestionEditor v-for="(q, idx) in questions" :key="q.id"
              :question="q" :index="idx" :total-questions="questions.length"
              @move="moveQuestion" @remove="removeQuestion" />
        </div>

        <!-- Add Question -->
        <div class="flex flex-wrap gap-2">
          <SecondaryButton v-for="type in questionTypes" :key="type" @click="addQuestion(type)">
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
