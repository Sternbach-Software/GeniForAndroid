package app.familygem.constant.intdefs

import androidx.annotation.IntDef

@IntDef(ALL_MEDIA, SHARED_MEDIA, LOCAL_MEDIA, SHARED_AND_LOCAL_MEDIA)
@Retention(AnnotationRetention.SOURCE)
annotation class MediaType

/**
 * all media
 * */
const val ALL_MEDIA = 0

/**
 * only shared media objects (for all Gedcom)
 * */
const val SHARED_MEDIA = 1

/**
 * only (local media?) (no gc needed)
 * */
const val LOCAL_MEDIA = 2

/**
 * shared and local but only previewable images and videos (for the main menu)
 * */
const val SHARED_AND_LOCAL_MEDIA = 3
