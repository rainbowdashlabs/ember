/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {iconSetOf} from './iconName'

/**
 * Which part of a station's gear a picture belongs to, so the picker can group a long list.
 */
export type GearIconGroup = 'protection' | 'equipment' | 'medical' | 'youth' | 'general'

/**
 * One picture an inventory or a kind can be drawn with.
 *
 * <p>The catalogue is curated rather than the whole of either set. Every picture has to be
 * registered before it renders, and shipping fourteen hundred of them so a station can pick nine is
 * not a trade anybody wants. A picture nobody thought of costs one line here and one in the plugin
 * of its set.
 *
 * @property name     what is stored: a bare name is FontAwesome, a {@code ph:} prefix is Phosphor
 * @property labelKey the translation key under {@code inventory.icons}, so no label lives in code
 * @property aliases  further words the search matches, for what a station calls the thing
 * @property group    where it sits in the picker
 */
export interface GearIcon {
    name: string
    labelKey: string
    aliases: string[]
    group: GearIconGroup
}

function entry(name: string, group: GearIconGroup, aliases: string[] = []): GearIcon {
    return {name, labelKey: `inventory.icons.${iconSetOf(name).name}`, aliases, group}
}

/**
 * Every picture a station can choose, in the order the picker offers them.
 */
export const GEAR_ICONS: readonly GearIcon[] = [
    entry('helmet-safety', 'protection', ['helm', 'kopfschutz', 'feuerwehrhelm']),
    entry('shirt', 'protection', ['hemd', 'jacke', 'oberteil', 'uniform']),
    entry('ph:t-shirt', 'protection', ['t-shirt', 'shirt', 'oberteil']),
    entry('ph:hoodie', 'protection', ['pullover', 'pulli', 'hoodie', 'kapuze']),
    entry('ph:pants', 'protection', ['hose', 'einsatzhose', 'latzhose']),
    entry('vest', 'protection', ['weste', 'warnweste']),
    entry('vest-patches', 'protection', ['weste', 'abzeichen', 'einsatzweste']),
    entry('ph:baseball-cap', 'protection', ['kappe', 'mütze', 'basecap', 'schirmmütze']),
    entry('mitten', 'protection', ['handschuhe', 'fäustlinge']),
    entry('ph:boot', 'protection', ['stiefel', 'einsatzstiefel', 'gummistiefel', 'schuhe']),
    entry('ph:sneaker', 'protection', ['turnschuhe', 'sportschuhe', 'schuhe', 'halbschuhe']),
    entry('shoe-prints', 'protection', ['stiefel', 'schuhe', 'sohlen']),
    entry('ph:sock', 'protection', ['socke', 'socken', 'strümpfe']),
    entry('socks', 'protection', ['socken', 'strümpfe']),
    entry('mask', 'protection', ['maske', 'atemschutz']),
    entry('head-side-mask', 'protection', ['atemschutzmaske', 'maske', 'filter']),
    entry('glasses', 'protection', ['brille', 'schutzbrille']),

    entry('radio', 'equipment', ['funk', 'funkgerät', 'feststation']),
    entry('walkie-talkie', 'equipment', ['funk', 'handfunkgerät', 'hfg']),
    entry('headphones', 'equipment', ['kopfhörer', 'headset', 'gehörschutz']),
    entry('toolbox', 'equipment', ['werkzeugkasten', 'werkzeug', 'kiste']),
    entry('screwdriver-wrench', 'equipment', ['werkzeug', 'schrauben', 'reparatur']),
    entry('wrench', 'equipment', ['schlüssel', 'werkzeug']),
    entry('hammer', 'equipment', ['hammer', 'werkzeug']),
    entry('fire-extinguisher', 'equipment', ['feuerlöscher', 'löscher']),
    entry('ph:ladder', 'equipment', ['leiter', 'steckleiter', 'anlegeleiter']),
    entry('water-ladder', 'equipment', ['leiter', 'steckleiter']),
    entry('ph:axe', 'equipment', ['axt', 'beil', 'feuerwehraxt']),
    entry('ph:shovel', 'equipment', ['schaufel', 'spaten']),
    entry('ph:flashlight', 'equipment', ['handlampe', 'taschenlampe', 'stablampe']),
    entry('lightbulb', 'equipment', ['lampe', 'licht', 'leuchte']),
    entry('battery-full', 'equipment', ['akku', 'batterie', 'ladung']),
    entry('plug', 'equipment', ['ladegerät', 'stecker', 'strom']),
    entry('ph:gas-can', 'equipment', ['kanister', 'benzin', 'kraftstoff']),
    entry('ph:traffic-cone', 'equipment', ['pylone', 'leitkegel', 'absperrung', 'hütchen']),
    entry('ph:lifebuoy', 'equipment', ['rettungsring', 'wasserrettung']),
    entry('ph:binoculars', 'equipment', ['fernglas', 'beobachtung']),
    entry('ph:fire-truck', 'equipment', ['fahrzeug', 'lf', 'mtw', 'löschfahrzeug']),
    entry('ph:siren', 'equipment', ['blaulicht', 'sirene', 'signal']),
    entry('broom', 'equipment', ['besen', 'reinigung']),
    entry('trowel', 'equipment', ['kelle', 'gerät']),

    entry('kit-medical', 'medical', ['erste hilfe', 'verbandkasten', 'sanität']),
    entry('suitcase-medical', 'medical', ['notfallkoffer', 'sanitätskoffer']),
    entry('truck-medical', 'medical', ['rettungswagen', 'sanitätsdienst']),

    entry('dice', 'youth', ['würfel', 'spiel', 'spiele']),
    entry('dice-d20', 'youth', ['würfel', 'rollenspiel', 'spiel']),
    entry('book', 'youth', ['buch', 'ausbildung', 'unterricht']),
    entry('book-open', 'youth', ['buch', 'lernen', 'unterlagen']),
    entry('tent', 'youth', ['zelt', 'zeltlager', 'lager']),
    entry('ph:campfire', 'youth', ['lagerfeuer', 'feuer', 'zeltlager']),
    entry('flag', 'youth', ['fahne', 'flagge', 'wimpel']),
    entry('compass', 'youth', ['kompass', 'orientierung']),
    entry('ph:map-trifold', 'youth', ['karte', 'landkarte', 'orientierung']),
    entry('ruler', 'youth', ['lineal', 'messen', 'maßband']),
    entry('camera', 'youth', ['kamera', 'foto']),
    entry('bullhorn', 'youth', ['megafon', 'durchsage', 'lautsprecher']),
    entry('graduation-cap', 'youth', ['ausbildung', 'prüfung', 'lehrgang']),

    entry('box', 'general', ['kiste', 'karton', 'behälter']),
    entry('box-open', 'general', ['kiste', 'offen']),
    entry('boxes-stacked', 'general', ['kisten', 'lager', 'stapel']),
    entry('cube', 'general', ['teil', 'gegenstand']),
    entry('warehouse', 'general', ['lager', 'halle', 'gerätehaus']),
    entry('suitcase', 'general', ['koffer', 'tasche']),
    entry('ph:backpack', 'general', ['rucksack', 'tornister']),
    entry('bag-shopping', 'general', ['tasche', 'beutel', 'einkauf']),
    entry('briefcase', 'general', ['aktentasche', 'unterlagen']),
    entry('layer-group', 'general', ['sammlung', 'schichten', 'gruppe']),
    entry('folder', 'general', ['ordner', 'mappe']),
    entry('key', 'general', ['schlüssel', 'schließung', 'spind']),
    entry('stapler', 'general', ['tacker', 'hefter', 'büro']),
    entry('hand-holding', 'general', ['leihe', 'geliehen', 'übergabe']),
] as const

const BY_NAME = new Map(GEAR_ICONS.map(icon => [icon.name, icon]))

/**
 * The catalogue entry for a stored name, or null for one the catalogue does not offer.
 *
 * <p>A name that is no longer offered still renders, because a station chose it before the
 * catalogue changed and the column keeps what it was given.
 *
 * @param name the stored FontAwesome name
 */
export function gearIcon(name: string | null | undefined): GearIcon | null {
    if (!name) return null
    return BY_NAME.get(name) ?? null
}
