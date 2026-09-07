/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import CreateInventoryBasicStep from './CreateInventoryBasicStep.vue'
import CreateInventorySizesStep from './CreateInventorySizesStep.vue'
import {InventoryTypes, type InventoryTypeName} from '@/api/inventory'
import {inventory} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'

const show = defineModel<boolean>({default: false})

const emit = defineEmits<{
  created: []
  error: []
}>()

const {t} = useI18n()

const step = ref<'basic' | 'sizes'>('basic')
const name = ref('')
const type = ref<InventoryTypeName>(InventoryTypes.INTERNAL)
const hasSizes = ref(false)
const homogeneous = ref(true)
const sizes = ref<string[]>([])

function reset() {
  step.value = 'basic'
  name.value = ''
  type.value = InventoryTypes.INTERNAL
  hasSizes.value = false
  // One thing in many copies is the permissive kind, so it is what a new inventory starts as
  homogeneous.value = true
  sizes.value = []
}

watch(show, (visible) => {
  if (visible) reset()
})

function nextStep() {
  if (hasSizes.value) {
    step.value = 'sizes'
  } else {
    submit()
  }
}

const {running: saving, run: runCreate} = useAsyncAction(async () => {
  const inv = await inventory.createInventory({
    name: name.value,
    inventoryType: type.value,
    hasSizes: hasSizes.value,
    homogeneous: homogeneous.value,
  })
  if (hasSizes.value && sizes.value.length > 0) {
    for (let i = 0; i < sizes.value.length; i++) {
      await inventory.createSize(inv.id, {label: sizes.value[i], position: i})
    }
  }
  show.value = false
  emit('created')
  return true
})

async function submit() {
  const ok = await runCreate()
  if (!ok) emit('error')
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ t('inventory.manage.create') }}</SubHeader>

      <CreateInventoryBasicStep
        v-if="step === 'basic'"
        v-model:name="name"
        v-model:type="type"
        v-model:hasSizes="hasSizes"
        v-model:homogeneous="homogeneous"
        @cancel="show = false"
        @next="nextStep"
      />

      <CreateInventorySizesStep
        v-if="step === 'sizes'"
        v-model:sizes="sizes"
        :saving="saving"
        @back="step = 'basic'"
        @submit="submit"
      />
    </div>
  </Modal>
</template>
