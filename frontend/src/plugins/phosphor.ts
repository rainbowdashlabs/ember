/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PhAxe, PhBackpack, PhBaseballCap, PhBinoculars, PhBoot, PhCampfire, PhFireTruck, PhFlashlight,
    PhGasCan, PhHoodie, PhLadder, PhLifebuoy, PhMapTrifold, PhPants, PhShovel, PhSiren, PhSneaker,
    PhSock, PhTrafficCone, PhTShirt,
} from '@phosphor-icons/vue'

/**
 * The second icon set, for the pictures FontAwesome's free tier does not draw.
 *
 * <p>FontAwesome stays the set a screen reaches for. This one is the answer to "there is no icon for
 * that", which is a fire service product's daily complaint: the free tier draws neither a siren nor a
 * ladder nor an engine, and its nearest pictures sit behind the paid tier.
 *
 * <p>Registered one by one and never through the package's own plugin, which registers all nine
 * thousand and takes the bundler's ability to leave out what nobody uses with it. The import list is
 * therefore the whole inventory, the way it is for FontAwesome, and `lint-icons` reads it to check
 * that every icon a template names is here.
 *
 * <p>Drawn at the `fill` weight wherever they stand beside FontAwesome, which is solid. The other
 * five weights are outlines and read as a different set on the same screen.
 */
const icons = {
    PhAxe,
    PhBackpack,
    PhBaseballCap,
    PhBinoculars,
    PhBoot,
    PhCampfire,
    PhFireTruck,
    PhFlashlight,
    PhGasCan,
    PhHoodie,
    PhLadder,
    PhLifebuoy,
    PhMapTrifold,
    PhPants,
    PhShovel,
    PhSiren,
    PhSneaker,
    PhSock,
    PhTrafficCone,
    PhTShirt,
}

export default defineNuxtPlugin((nuxtApp) => {
    for (const [name, component] of Object.entries(icons)) {
        nuxtApp.vueApp.component(name, component)
    }
})
