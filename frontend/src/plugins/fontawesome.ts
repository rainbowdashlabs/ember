/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {library, config} from '@fortawesome/fontawesome-svg-core'
import '@fortawesome/fontawesome-svg-core/styles.css'

config.autoAddCss = false
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import {
    faAngleDown, faAngleUp, faAnglesDown, faAnglesUp, faArrowDownWideShort, faArrowRight,
    faArrowsRotate, faArrowUpWideShort, faEllipsis, faEquals, faAsterisk, faBan, faBars, faBell, faBold,
    faBoxesStacked, faBoxOpen, faBuilding, faCalendarDays, faCalendarPlus, faChartLine, faCheck,
    faCheckDouble, faChevronDown, faChevronLeft, faChevronRight, faChevronUp, faCircle,
    faCircleCheck, faCircleDot, faCircleInfo, faClipboardCheck, faClipboardList, faClipboardUser,
    faCode, faClockRotateLeft, faComment, faCopy, faDownload, faEye, faFileCode, faFileExport,
    faFire, faFolderPlus, faGauge, faGear, faGears, faGlobe, faGripVertical, faHardDrive, faHashtag, faHeading,
    faHighlighter, faHouse, faIdCard, faItalic, faLayerGroup, faAlignCenter, faAlignLeft,
    faAlignRight, faLink, faLinkSlash, faList, faListOl, faListUl, faLock, faMedal, faMinus,
    faNewspaper, faMobileScreen, faMoon, faParagraph, faPen, faPenToSquare, faPeopleGroup,
    faPhone, faPlus, faQuoteLeft, faRainbow, faRightFromBracket, faRightToBracket, faRss,
    faRotate, faScissors, faShield, faShuffle, faScaleBalanced, faSliders, faSort, faSortDown,
    faSortUp, faSpinner, faStrikethrough, faSun, faTableColumns, faTrash, faTriangleExclamation,
    faUnderline, faUpload, faUser, faUserCheck, faUserMinus, faUserPlus, faUserSlash, faUsers,
    faUsersGear, faXmark, faXmarkCircle, faCircleQuestion, faFilter, faTags,
    faSquarePollVertical, faStar, faHeart, faThumbsUp, faGripLines, faLocationDot, faCamera,
    faToggleOn, faArrowRightArrowLeft, faPuzzlePiece, faPalette, faBook, faServer, faDatabase,
    faEnvelope, faArrowDown, faBug, faFlag, faPaperclip, faInbox, faExpand, faRocket,
    faShareNodes, faHandshake, faRobot, faCircleXmark, faRotateLeft, faCodeCompare, faTrophy,
    faRedo, faArrowLeft, faHandHolding, faCalendarXmark, faCheckCircle, faEyeSlash, faDesktop,
    faUserGear, faCalendar, faChartBar, faChartPie, faClock, faHand, faImage, faPaperPlane,
    faPlug, faTag, faUmbrellaBeach, faUserShield, faUserTie, faListCheck, faCircleHalfStroke,
    faSquare, faSquareCheck, faGraduationCap, faBrain, faFileLines, faFilePdf, faFileImport,
    faFlask, faPlay, faBookOpen, faFolder, faFolderOpen, faFile, faMagnifyingGlass, faCompass,
    faFileCsv, faReply, faShieldHalved, faAt, faPaste, faClone, faHourglassHalf, faArrowUp,
    faSatelliteDish, faMapLocationDot, faUserClock, faFloppyDisk, faBoxArchive,
    faFilePowerpoint, faDisplay, faKey, faArrowsUpDown, faLightbulb, faBroom, faTowerBroadcast,
} from '@fortawesome/free-solid-svg-icons'
import {
    faGithub, faWindows, faApple, faLinux, faAndroid, faChrome, faFirefoxBrowser, faSafari,
    faEdge, faOpera, faYoutube,
} from '@fortawesome/free-brands-svg-icons'

library.add(
    faSun, faMoon, faCheck, faAngleDown, faAngleUp, faAnglesDown, faAnglesUp, faEllipsis,
    faEquals, faXmark, faXmarkCircle, faSpinner, faCircleInfo, faCircleCheck, faCircle,
    faCircleDot, faTriangleExclamation, faDownload, faUpload, faTrash, faPen, faLock,
    faRightFromBracket, faRightToBracket, faBars, faGauge, faChevronDown, faChevronRight,
    faHouse, faChartLine, faShield, faBuilding, faGears, faUsers, faUserPlus, faList,
    faLayerGroup, faBoxesStacked, faBoxOpen, faClipboardUser, faCalendarPlus, faClockRotateLeft,
    faClipboardCheck, faUsersGear, faPlus, faChevronLeft, faChevronUp, faGripVertical, faCopy,
    faBell, faPhone, faMobileScreen, faIdCard, faHashtag, faFire, faMedal, faRainbow,
    faCalendarDays, faPenToSquare, faFolderPlus, faClipboardList, faUser, faSort, faSortUp,
    faSortDown, faAsterisk, faEye, faLink, faTableColumns, faBan, faComment, faCheckDouble,
    faMinus, faRotate, faRss, faScissors, faNewspaper, faGear, faPeopleGroup, faArrowRight,
    faFileExport, faGithub, faWindows, faApple, faLinux, faAndroid, faChrome, faFirefoxBrowser,
    faSafari, faEdge, faOpera, faGlobe, faUserSlash, faUserCheck, faCircleQuestion, faFilter,
    faTags, faSquarePollVertical, faStar, faHeart, faThumbsUp, faGripLines, faLocationDot,
    faCamera, faToggleOn, faArrowRightArrowLeft, faPuzzlePiece, faBook, faServer, faDatabase,
    faHardDrive, faArrowsRotate, faEnvelope, faArrowDown, faUserGear, faCalendar, faChartBar, faChartPie, faClock, faHand,
    faImage, faPaperPlane, faPlug, faTag, faUmbrellaBeach, faUserMinus, faUserShield, faUserTie,
    faListCheck, faScaleBalanced, faSliders, faCircleHalfStroke, faPalette, faSquare,
    faSquareCheck, faGraduationCap, faBrain, faFileLines, faFilePdf, faFileImport, faFlask,
    faShuffle, faPlay, faBookOpen, faFolder, faFolderOpen, faFile, faMagnifyingGlass, faYoutube,
    faBold, faItalic, faUnderline, faStrikethrough, faCode, faListUl, faListOl, faQuoteLeft,
    faFileCode, faParagraph, faHeading, faHighlighter, faLinkSlash, faAlignLeft, faAlignCenter,
    faAlignRight, faBug, faShareNodes, faHandshake, faCompass, faRobot, faCircleXmark,
    faRotateLeft, faCodeCompare, faTrophy, faRedo, faArrowLeft, faHandHolding, faCalendarXmark,
    faCheckCircle, faEyeSlash, faDesktop, faFlag, faPaperclip, faInbox, faExpand, faRocket,
    faArrowDownWideShort, faArrowUpWideShort, faFileCsv, faReply, faShieldHalved, faAt,
    faPaste, faClone, faHourglassHalf, faArrowUp, faSatelliteDish, faMapLocationDot,
    faUserClock, faFloppyDisk, faBoxArchive, faFilePowerpoint, faDisplay, faKey,
    faArrowsUpDown, faLightbulb, faBroom, faTowerBroadcast,
)

export default defineNuxtPlugin((nuxtApp) => {
    nuxtApp.vueApp.component('font-awesome-icon', FontAwesomeIcon)
})
