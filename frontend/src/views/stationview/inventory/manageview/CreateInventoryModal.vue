/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import CreateInventoryBasicStep from './CreateInventoryBasicStep.vue'
import CreateInventorySizesStep from './CreateInventorySizesStep.vue'
import {InventoryTypes, type InventoryTypeName} from '@/api/inventory'
import {inventory} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'

const show = defineModel<boolean>({default: false})

const emit = defineEmits<{
  created: []
}>()

const {t} = useI18n()

const step = ref<'basic' | 'sizes'>('basic')
const name = ref('')
const type = ref<InventoryTypeName>(InventoryTypes.INTERNAL)
const hasSizes = ref(false)
const homogeneous = ref(true)
const sizes = ref<string[]>([])
const icon = ref<string | null>(null)
const color = ref<string | null>(null)

/** Empties the form. One thing in many copies is the permissive kind, so a new inventory starts as that. */
function reset() {
  step.value = 'basic'
  name.value = ''
  type.value = InventoryTypes.INTERNAL
  hasSizes.value = false
  homogeneous.value = true
  sizes.value = []
  icon.value = null
  color.value = null
  sizesFailure.value = null
  clearCreateFailure()
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

const {
  running: creating,
  failure: createFailure,
  run: runCreate,
  clearError: clearCreateFailure,
} = useAsyncAction(() => inventory.createInventory({
  name: name.value,
  inventoryType: type.value,
  hasSizes: hasSizes.value,
  homogeneous: homogeneous.value,
  icon: icon.value,
  color: color.value,
}))

const sizesFailure = ref<Failure | null>(null)
const addingSizes = ref(false)

const saving = computed(() => creating.value || addingSizes.value)

const failure = computed(() => createFailure.value ?? sizesFailure.value)

/**
 * Creates the inventory, then the sizes it was given.
 *
 * <p>Two writes behind one button, caught apart. Once the first is through the inventory exists, and a
 * reader told plainly that creating failed presses the button again and ends up with two of them. Where
 * the sizes were what failed, the screen says that instead: the inventory is there, and its sizes are
 * added under its settings.
 */
async function submit() {
  sizesFailure.value = null
  const created = await runCreate()
  if (!created) return

  if (hasSizes.value && sizes.value.length > 0) {
    addingSizes.value = true
    try {
      for (let i = 0; i < sizes.value.length; i++) {
        await inventory.createSize(created.id, {label: sizes.value[i], position: i})
      }
    } catch (e) {
      sizesFailure.value = {...describeFailure(e, t), message: t('inventory.manage.createdWithoutSizes')}
      emit('created')
      return
    } finally {
      addingSizes.value = false
    }
  }

  show.value = false
  emit('created')
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ t('inventory.manage.create') }}</SubHeader>

      <FailureAlert :failure="failure"/>

      <CreateInventoryBasicStep
        v-if="step === 'basic'"
        v-model:name="name"
        v-model:type="type"
        v-model:hasSizes="hasSizes"
        v-model:homogeneous="homogeneous"
        v-model:icon="icon"
        v-model:color="color"
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
