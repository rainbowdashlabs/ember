/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import {RuleNodeKind, type RuleNode} from '@/api/generated/schema'
import type {RuleCatalog} from './ruleCatalog'
import RuleCheckRow from './RuleCheckRow.vue'
import {emptyBlock, emptyCheck} from './ruleDraft'

/**
 * One block of a rule: whether all, any or none of its children have to hold, the children themselves (checks
 * and further blocks, as deep as the rule goes) and the buttons to add either. The root block cannot be removed.
 */
const props = withDefaults(defineProps<{
  block: RuleNode
  catalog: RuleCatalog
  root?: boolean
  /** How deep this block sits, one for the root, which decides whether another level still fits. */
  level?: number
  /** Whether the rule holds as many checks as a rule may, so nothing more can be added. */
  full?: boolean
}>(), {level: 1})

const emit = defineEmits<{
  change: [block: RuleNode]
  remove: []
}>()

const {t} = useI18n()

const kinds = [RuleNodeKind.ALL, RuleNodeKind.ANY, RuleNodeKind.NONE]
const kindLabels: Partial<Record<RuleNodeKind, string>> = {
  [RuleNodeKind.ALL]: 'memberRules.all',
  [RuleNodeKind.ANY]: 'memberRules.any',
  [RuleNodeKind.NONE]: 'memberRules.none',
}

const mayAddCheck = computed(() => !props.full && props.level + 1 <= props.catalog.limits.depth)
const mayAddBlock = computed(() => !props.full && props.level + 2 <= props.catalog.limits.depth)

function setKind(kind: string | number | null | undefined) {
  emit('change', {...props.block, kind: kind as RuleNodeKind})
}

function setChild(index: number, child: RuleNode) {
  const children = [...props.block.children]
  children[index] = child
  emit('change', {...props.block, children})
}

function removeChild(index: number) {
  emit('change', {...props.block, children: props.block.children.filter((_, at) => at !== index)})
}

function add(child: RuleNode) {
  emit('change', {...props.block, children: [...props.block.children, child]})
}
</script>

<template>
  <div class="space-y-3 rounded-lg border border-(--border) p-2 sm:p-3">
    <div class="flex items-center justify-between gap-2">
      <SelectInput :model-value="block.kind" :aria-label="t('memberRules.kind')" @update:model-value="setKind">
        <option v-for="kind in kinds" :key="kind" :value="kind">{{ t(kindLabels[kind]!) }}</option>
      </SelectInput>
      <IconButton v-if="!root" :icon="['fas', 'trash']" :label="t('memberRules.removeNode')" @click="emit('remove')"/>
    </div>
    <ul class="space-y-3 border-l-2 border-(--border) pl-1.5 sm:pl-3">
      <li v-for="(child, index) in block.children" :key="index">
        <RuleCheckRow v-if="child.kind === RuleNodeKind.CHECK" :check="child" :catalog="catalog"
                      @change="next => setChild(index, next)" @remove="removeChild(index)"/>
        <RuleBlockEditor v-else :block="child" :catalog="catalog" :level="level + 1" :full="full"
                         @change="next => setChild(index, next)" @remove="removeChild(index)"/>
      </li>
    </ul>
    <ButtonRow v-if="mayAddCheck" pair>
      <SecondaryButton :icon="['fas', 'plus']" @click="add(emptyCheck())">{{ t('memberRules.addCheck') }}</SecondaryButton>
      <SecondaryButton v-if="mayAddBlock" :icon="['fas', 'plus']" @click="add(emptyBlock())">
        {{ t('memberRules.addBlock') }}
      </SecondaryButton>
    </ButtonRow>
  </div>
</template>
