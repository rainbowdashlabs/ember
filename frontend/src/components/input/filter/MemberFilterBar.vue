/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

export interface FilterOption {
  id: number
  name: string
}

export interface FilterCriteria {
  roleIds: number[]
  groupIds: number[]
  tagIds: number[]
  mode: 'AND' | 'OR'
}

const props = withDefaults(
  defineProps<{
    roles?: FilterOption[]
    groups?: FilterOption[]
    tags?: FilterOption[]
    enabledFilters?: ('roles' | 'groups' | 'tags')[]
  }>(),
  {
    roles: () => [],
    groups: () => [],
    tags: () => [],
    enabledFilters: undefined,
  },
)

const emit = defineEmits<{
  filter: [criteria: FilterCriteria]
}>()

const { t } = useI18n()

const state = reactive({
  selectedRoleIds: new Set<number>(),
  selectedGroupIds: new Set<number>(),
  selectedTagIds: new Set<number>(),
  mode: 'AND' as 'AND' | 'OR',
  sectionsVisible: {
    roles: true,
    groups: true,
    tags: true,
  },
})

const visibleSections = computed(() => {
  const enabled = props.enabledFilters
  return {
    roles: (props.roles.length > 0) && (!enabled || enabled.includes('roles')),
    groups: (props.groups.length > 0) && (!enabled || enabled.includes('groups')),
    tags: (props.tags.length > 0) && (!enabled || enabled.includes('tags')),
  }
})

const hasAnyFilter = computed(() =>
  visibleSections.value.roles || visibleSections.value.groups || visibleSections.value.tags,
)

const hasActiveSelection = computed(() =>
  state.selectedRoleIds.size > 0 || state.selectedGroupIds.size > 0 || state.selectedTagIds.size > 0,
)

function toggleInSet(set: Set<number>, id: number) {
  if (set.has(id)) {
    set.delete(id)
  } else {
    set.add(id)
  }
}

function toggleMode() {
  state.mode = state.mode === 'AND' ? 'OR' : 'AND'
}

function toggleSection(section: 'roles' | 'groups' | 'tags') {
  state.sectionsVisible[section] = !state.sectionsVisible[section]
}

function reset() {
  state.selectedRoleIds.clear()
  state.selectedGroupIds.clear()
  state.selectedTagIds.clear()
}

function buildCriteria(): FilterCriteria {
  return {
    roleIds: [...state.selectedRoleIds],
    groupIds: [...state.selectedGroupIds],
    tagIds: [...state.selectedTagIds],
    mode: state.mode,
  }
}

watch(
  () => buildCriteria(),
  (criteria) => emit('filter', criteria),
  { deep: true },
)
</script>

<template>
  <div v-if="hasAnyFilter" class="flex flex-wrap items-center gap-3">
    <!-- AND/OR toggle -->
    <SelectionToggleButton
      :selected="state.mode === 'AND'"
      size="sm"
      @toggle="toggleMode"
    >
      {{ state.mode === 'AND' ? t('filter.and') : t('filter.or') }}
    </SelectionToggleButton>

    <!-- Roles section -->
    <template v-if="visibleSections.roles">
      <SelectionToggleButton
        :selected="state.sectionsVisible.roles"
        size="sm"
        @toggle="toggleSection('roles')"
      >
        {{ t('filter.roles') }}
      </SelectionToggleButton>
      <template v-if="state.sectionsVisible.roles">
        <SelectionToggleButton
          v-for="role in roles"
          :key="'role-' + role.id"
          :selected="state.selectedRoleIds.has(role.id)"
          size="sm"
          @toggle="toggleInSet(state.selectedRoleIds, role.id)"
        >
          {{ role.name }}
        </SelectionToggleButton>
      </template>
    </template>

    <!-- Groups section -->
    <template v-if="visibleSections.groups">
      <SelectionToggleButton
        :selected="state.sectionsVisible.groups"
        size="sm"
        @toggle="toggleSection('groups')"
      >
        {{ t('filter.groups') }}
      </SelectionToggleButton>
      <template v-if="state.sectionsVisible.groups">
        <SelectionToggleButton
          v-for="group in groups"
          :key="'group-' + group.id"
          :selected="state.selectedGroupIds.has(group.id)"
          size="sm"
          @toggle="toggleInSet(state.selectedGroupIds, group.id)"
        >
          {{ group.name }}
        </SelectionToggleButton>
      </template>
    </template>

    <!-- Tags section -->
    <template v-if="visibleSections.tags">
      <SelectionToggleButton
        :selected="state.sectionsVisible.tags"
        size="sm"
        @toggle="toggleSection('tags')"
      >
        {{ t('filter.tags') }}
      </SelectionToggleButton>
      <template v-if="state.sectionsVisible.tags">
        <SelectionToggleButton
          v-for="tag in tags"
          :key="'tag-' + tag.id"
          :selected="state.selectedTagIds.has(tag.id)"
          size="sm"
          @toggle="toggleInSet(state.selectedTagIds, tag.id)"
        >
          {{ tag.name }}
        </SelectionToggleButton>
      </template>
    </template>

    <!-- Reset button -->
    <SecondaryButton
      v-if="hasActiveSelection"
      @click="reset"
    >
      <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" />
      {{ t('filter.reset') }}
    </SecondaryButton>
  </div>
</template>
