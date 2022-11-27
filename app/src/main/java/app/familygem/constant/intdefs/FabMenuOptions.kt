package app.familygem.constant.intdefs

import androidx.annotation.IntDef

@IntDef
@Retention(AnnotationRetention.SOURCE)
annotation class FabMenuOptions
const val ADDRESS_OPTION = 0
const val LINK_ENTITY_OPTION = 100
const val NEW_REPOSITORY_OPTION = 101
const val LINK_REPOSITORY_OPTION = 102
const val NEW_NOTE_OPTION = 103
const val NEW_SHARED_NOTE_OPTION = 104
const val LINK_SHARED_NOTE_OPTION = 105
const val NEW_MEDIA_OPTION = 106
const val NEW_SHARED_MEDIA_OPTION = 107
const val LINK_SHARED_MEDIA_OPTION = 108
const val NEW_SOURCE_CITATION_OPTION = 109
const val NEW_SOURCE_OPTION = 110
const val LINK_SOURCE_OPTION = 111
const val LINK_NEW_PARENT_OR_PARTNER_OPTION = 120
const val LINK_NEW_CHILD_OPTION = 121
const val LINK_EXISTING_PARENT_OR_PARTNER_OPTION = 122
const val LINK_EXISTING_CHILD_OPTION = 123
const val MARRIAGE_OPTION = 124
const val DIVORCE_OPTION = 125
const val EVENT_OPTION = 200
val Int.isAddress get() = this < 100
val Int.isEventOption get() = this >= 200