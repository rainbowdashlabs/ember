/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createApp} from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'

import {library} from '@fortawesome/fontawesome-svg-core'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import {
    faArrowRight,
    faAsterisk,
    faBan,
    faBars,
    faBell,
    faBoxesStacked,
    faBoxOpen,
    faBuilding,
    faCalendarDays,
    faCalendarPlus,
    faChartLine,
    faCheck,
    faCheckDouble,
    faChevronDown,
    faChevronLeft,
    faChevronRight,
    faChevronUp,
    faCircleCheck,
    faCircleInfo,
    faClipboardCheck,
    faClipboardList,
    faClipboardUser,
    faClockRotateLeft,
    faComment,
    faCopy,
    faDownload,
    faEye,
    faFileExport,
    faFire,
    faFolderPlus,
    faGauge,
    faGear,
    faGears,
    faGlobe,
    faGripVertical,
    faHashtag,
    faHouse,
    faIdCard,
    faLayerGroup,
    faLink,
    faList,
    faLock,
    faMedal,
    faMinus,
    faNewspaper,
    faMobileScreen,
    faMoon,
    faPen,
    faPenToSquare,
    faPeopleGroup,
    faPhone,
    faPlus,
    faRainbow,
    faRightFromBracket,
    faRotate,
    faScissors,
    faShield,
    faSort,
    faSortDown,
    faSortUp,
    faSpinner,
    faSun,
    faTableColumns,
    faTrash,
    faTriangleExclamation,
    faUpload,
    faUser,
    faUserCheck,
    faUserPlus,
    faUserSlash,
    faUsers,
    faUsersGear,
    faXmark,
    faXmarkCircle,
    faCircleQuestion,
    faFilter,
    faTags,
    faSquarePollVertical,
    faStar,
    faHeart,
    faThumbsUp,
    faGripLines,
    faLocationDot,
    faCamera,
    faToggleOn,
    faArrowRightArrowLeft,
    faPuzzlePiece,
    faBook,
    faServer,
    faDatabase,
    faEnvelope,
    faArrowDown,
    faUserGear,
    faCalendar,
    faChartBar,
    faChartPie,
    faClock,
    faHand,
    faImage,
    faPaperPlane,
    faPlug,
    faTag,
    faUmbrellaBeach,
    faUserShield,
    faUserTie,
    faListCheck
} from '@fortawesome/free-solid-svg-icons'
import {
    faGithub,
    faWindows,
    faApple,
    faLinux,
    faAndroid,
    faChrome,
    faFirefoxBrowser,
    faSafari,
    faEdge,
    faOpera,
} from '@fortawesome/free-brands-svg-icons'
import {initTokenRefresh} from '@/api/client'

library.add(faSun, faMoon, faCheck, faXmark, faXmarkCircle, faSpinner, faCircleInfo, faCircleCheck, faTriangleExclamation, faDownload, faUpload, faTrash, faPen, faLock, faRightFromBracket, faBars, faGauge, faChevronDown, faChevronRight, faHouse, faChartLine, faShield, faBuilding, faGears, faUsers, faUserPlus, faList, faLayerGroup, faBoxesStacked, faBoxOpen, faClipboardUser, faCalendarPlus, faClockRotateLeft, faClipboardCheck, faUsersGear, faPlus, faChevronLeft, faChevronUp, faGripVertical, faCopy, faBell, faPhone, faMobileScreen, faIdCard, faHashtag, faFire, faMedal, faRainbow, faCalendarDays, faPenToSquare, faFolderPlus, faClipboardList, faUser, faSort, faSortUp, faSortDown, faAsterisk, faEye, faLink, faTableColumns, faBan, faComment, faCheckDouble, faMinus, faRotate, faScissors, faNewspaper, faGear, faPeopleGroup, faArrowRight, faFileExport, faGithub, faWindows, faApple, faLinux, faAndroid, faChrome, faFirefoxBrowser, faSafari, faEdge, faOpera, faGlobe, faUserSlash, faUserCheck, faCircleQuestion, faFilter, faTags, faSquarePollVertical, faStar, faHeart, faThumbsUp, faGripLines, faLocationDot, faCamera, faToggleOn, faArrowRightArrowLeft, faPuzzlePiece, faBook, faServer, faDatabase, faEnvelope, faArrowDown, faUserGear, faCalendar, faChartBar, faChartPie, faClock, faHand, faImage, faPaperPlane, faPlug, faTag, faUmbrellaBeach, faUserShield, faUserTie, faListCheck)

initTokenRefresh()

createApp(App)
    .component('font-awesome-icon', FontAwesomeIcon)
    .use(router)
    .use(i18n)
    .mount('#app')
