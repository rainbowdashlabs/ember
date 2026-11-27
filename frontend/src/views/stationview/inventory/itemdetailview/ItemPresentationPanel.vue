/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import InventoryPictureField from '@/components/inventory/InventoryPictureField.vue'
import PartnerDescriptionField from '@/components/inventory/PartnerDescriptionField.vue'
import {inventory, inventoryPictures} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * The text and photo of one piece as partner stations see them in the lending shop. Only an
 * inventory of different things lends its pieces one by one, so only there does a piece carry text
 * and a photo of its own.
 */
const props = defineProps<{
  itemId: number
  inventoryId: number
}>()

const emit = defineEmits<{
  pictureChanged: [pictureKey: string | null]
}>()

const {t} = useI18n()

const differentThings = ref(false)
const description = ref('')
const savedDescription = ref('')
const pictureKey = ref<string | null>(null)

const {failure} = useAsyncLoader(async () => {
  const [owner, presentation] = await Promise.all([
    inventory.getInventory(props.inventoryId),
    inventory.getItemPresentation(props.itemId),
  ])
  differentThings.value = !owner.homogeneous
  description.value = presentation.description
  savedDescription.value = presentation.description
  pictureKey.value = presentation.pictureKey ?? null
})

const pictureSrc = computed(() => pictureKey.value
    ? inventoryPictures.itemPictureUrl(props.itemId, pictureKey.value, 256)
    : null)
const upload = (file: File) => inventoryPictures.uploadItemPicture(props.itemId, file)
const remove = () => inventoryPictures.removeItemPicture(props.itemId)

function onPictureChanged(key: string | null) {
  pictureKey.value = key
  emit('pictureChanged', key)
}

async function save() {
  const saved = await inventory.describeItem(props.itemId, description.value)
  description.value = saved.description
  savedDescription.value = saved.description
}
</script>

<template>
  <FailureAlert v-if="failure" :failure="failure"/>
  <NeutralContainer v-else-if="differentThings" class="space-y-4" data-testid="item-presentation">
    <SubHeader>{{ t('inventory.description.itemTitle') }}</SubHeader>
    <MutedText tag="p">{{ t('inventory.description.itemIntro') }}</MutedText>
    <InventoryPictureField
        :hint="t('inventory.picture.itemHint')"
        :remove="remove"
        :src="pictureSrc"
        :upload="upload"
        @changed="onPictureChanged"
    />
    <PartnerDescriptionField v-model="description"/>
    <SaveButton :disabled="description === savedDescription" :action="save"/>
  </NeutralContainer>
</template>
