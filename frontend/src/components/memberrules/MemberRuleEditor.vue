/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {memberGroups, memberRules, userTags} from '@/api'
import type {RuleOwnerKind} from '@/api/memberRules'
import {RuleNodeKind, StationPermission, type RuleNode, type RulePreview} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'
import type {RuleCatalog} from './ruleCatalog'
import RuleBlockEditor from './RuleBlockEditor.vue'
import RulePreviewPanel from './RulePreviewPanel.vue'
import {countChecks, depthOf, emptyBlock, incompleteChecks, readsMemberData, RULE_LIMITS} from './ruleDraft'

/**
 * Whether a group or tag is kept by hand or by a rule, and the rule itself: the builder, a preview of who it
 * holds today and who would join or leave, and saving or removing it.
 *
 * <p>Switching to a rule changes nothing until the rule is saved, which asks first, since from then on the rule
 * alone decides. Switching back removes the rule after asking; the members it held stay and are kept by hand.
 * Private tags are never offered, because a rule cannot choose by one, and checks on member data only to a
 * reader who may read members.
 */
const props = defineProps<{
  kind: RuleOwnerKind
  ownerId: number
  /** Called after a rule was saved or removed, so the page can reload members and markers. */
  onChanged?: () => void
}>()

const {t} = useI18n()
const {hasPermission} = useSession()

const loading = ref(true)
const failure = ref<Failure | null>(null)
const catalog = ref<RuleCatalog | null>(null)
const saved = ref<RuleNode | null>(null)
const draft = ref<RuleNode | null>(null)
const preview = ref<RulePreview | null>(null)
const confirmSave = ref(false)
const confirmRemove = ref(false)

const byRule = computed(() => draft.value !== null)

/** A reader who may not read members cannot change a rule that reads member data, so it is shown read only. */
const locked = computed(() => {
  const rule = draft.value
  const loaded = catalog.value
  if (!rule || !loaded || loaded.readsMembers) return false
  return countChecksWhere(rule, readsMemberData) > 0
})

const missing = computed(() => {
  const rule = draft.value
  const loaded = catalog.value
  return rule && loaded ? incompleteChecks(rule, loaded.options, loaded.fields) : 0
})

const full = computed(() => draft.value !== null && countChecks(draft.value) >= RULE_LIMITS.checks)
const fits = computed(() => draft.value !== null
    && countChecks(draft.value) <= RULE_LIMITS.checks && depthOf(draft.value) <= RULE_LIMITS.depth)
const ready = computed(() => !locked.value && missing.value === 0 && fits.value)

function countChecksWhere(node: RuleNode, test: (check: RuleNode) => boolean): number {
  if (node.kind === RuleNodeKind.CHECK) return test(node) ? 1 : 0
  return node.children.reduce((sum, child) => sum + countChecksWhere(child, test), 0)
}

/** An edit of the draft: the preview showed an older rule, so it goes. */
function edited(next: RuleNode) {
  draft.value = next
  preview.value = null
}

async function loadCatalog(): Promise<RuleCatalog> {
  const readsMembers = hasPermission(StationPermission.MEMBER_READ)
  const [options, fields, groups, tags] = await Promise.all([
    memberRules.listOperators(),
    readsMembers ? memberRules.listFields() : Promise.resolve([]),
    memberGroups.listGroups(),
    userTags.listChoosableTags(),
  ])
  return {
    options,
    fields,
    groups: groups.map(group => ({id: group.id, name: group.name ?? ''})),
    tags: tags.map(tag => ({id: tag.id, name: tag.name})),
    readsMembers,
  }
}

async function load() {
  loading.value = true
  failure.value = null
  preview.value = null
  try {
    const [loaded, rule] = await Promise.all([
      catalog.value ? Promise.resolve(catalog.value) : loadCatalog(),
      memberRules.getRule(props.kind, props.ownerId),
    ])
    catalog.value = loaded
    saved.value = rule
    draft.value = rule
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.kind, props.ownerId], load)

function chooseMode(mode: string | number | null | undefined) {
  if (mode === 'rule') {
    draft.value = draft.value ?? saved.value ?? emptyBlock()
    return
  }
  if (saved.value) confirmRemove.value = true
  else draft.value = null
}

async function runPreview() {
  const rule = draft.value
  if (!rule) return
  failure.value = null
  try {
    preview.value = await memberRules.previewRule(props.kind, props.ownerId, rule)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

function requestSave() {
  if (saved.value) void save()
  else confirmSave.value = true
}

async function save() {
  const rule = draft.value
  if (!rule) return
  failure.value = null
  try {
    saved.value = await memberRules.saveRule(props.kind, props.ownerId, rule)
    draft.value = saved.value
    showToast(t('memberRules.saved'), 'success')
    props.onChanged?.()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function remove() {
  failure.value = null
  try {
    await memberRules.removeRule(props.kind, props.ownerId)
    saved.value = null
    draft.value = null
    preview.value = null
    props.onChanged?.()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <div class="space-y-3">
    <Spinner v-if="loading" size="sm"/>
    <template v-else>
      <div class="flex flex-wrap items-center gap-2">
        <FieldLabel>{{ t('memberRules.mode') }}</FieldLabel>
        <SelectInput :model-value="byRule ? 'rule' : 'hand'" :aria-label="t('memberRules.mode')"
                     @update:model-value="chooseMode">
          <option value="hand">{{ t('memberRules.handPicked') }}</option>
          <option value="rule">{{ t('memberRules.byRule') }}</option>
        </SelectInput>
      </div>
      <FailureAlert :failure="failure"/>
      <template v-if="draft && catalog">
        <MutedText tag="p">{{ t('memberRules.keptByRuleHint') }}</MutedText>
        <MutedText v-if="locked" tag="p">{{ t('memberRules.needsMemberRead') }}</MutedText>
        <fieldset :disabled="locked" class="min-w-0">
          <RuleBlockEditor :block="draft" :catalog="catalog" :full="full" root @change="edited"/>
        </fieldset>
        <MutedText v-if="missing > 0" tag="p" class="text-error">
          {{ t('memberRules.incompleteCount', {count: missing}) }}
        </MutedText>
        <RulePreviewPanel v-if="preview" :preview="preview"/>
        <ButtonRow v-if="!locked">
          <SecondaryButton :icon="['fas', 'eye']" :disabled="!ready" @click="runPreview">
            {{ t('memberRules.preview.refresh') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'floppy-disk']" :disabled="!ready" @click="requestSave">
            {{ t('memberRules.save') }}
          </PrimaryButton>
          <ErrorButton v-if="saved" :icon="['fas', 'trash']" @click="confirmRemove = true">
            {{ t('memberRules.removeRule') }}
          </ErrorButton>
        </ButtonRow>
      </template>
    </template>
    <ConfirmDeleteModal v-model="confirmSave" :message="t('memberRules.switchConfirm')"
                        :confirm-label="t('memberRules.save')" @confirm="save"/>
    <ConfirmDeleteModal v-model="confirmRemove" :message="t('memberRules.removeConfirm')"
                        :confirm-label="t('memberRules.removeRule')" @confirm="remove"/>
  </div>
</template>
