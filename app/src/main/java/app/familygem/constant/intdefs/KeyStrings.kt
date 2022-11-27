package app.familygem.constant.intdefs

import androidx.annotation.StringDef

@StringDef(
    THIS_PERSON_KEY,
    PROFILE_ID_KEY,
    FAMILY_ID_KEY,
    PIVOT_ID_KEY,
    CHILD_FAM_ID_KEY,
    SPOUSE_FAM_ID_KEY,
    FRAGMENT_KEY,
    NEW_RELATIVE_KEY,
    OPEN_TREE_AUTOMATICALLY_KEY,
    AUTO_OPENED_TREE_KEY,
    CONSUMED_NOTIFICATIONS_KEY,
    GENERATION_KEY,
    TREE_ID_KEY,
    TREE_ID_KEY_ENGLISH,
    TREE_2_ID_KEY,
    DATA_ID_KEY,
    ID_KEY,
    POSITION_KEY,
    RELATIONSHIP_ID_KEY,
    PATH_KEY,
    URI_KEY,
    CARD_KEY,
    CITATION_KEY,
    LOCATION_KEY,
    PEOPLE_LIST_CHOOSE_RELATIVE_KEY,
    NEW_PERSON_VALUE,
    NEW_FAMILY_OF_VALUE,
    EXISTING_FAMILY_VALUE,
    CACHE_EXTENSION_KEY,
    PASSED_EXTENSION_KEY,
    ZONE_EXTENSION_KEY,
    TITLE_KEY,
    SOURCE_ID_KEY,
    TEXT_KEY,
    INDI_ID_KEY,
    NOTE_ID_KEY,
    REPO_ID_KEY,
    SOURCE_EXTENSION_KEY,
    FROM_NOTES_KEY,
    RELATIVE_ID_KEY,
    SOURCE_ID_KEY_ENGLISH,
    GALLERY_CHOOSE_MEDIA_KEY,
    MEDIA_ID_KEY,
    IS_ALONE_KEY,
    LIBRARY_CHOOSE_SOURCE_KEY
)
@Retention(AnnotationRetention.RUNTIME)
annotation class KeyStrings

const val THIS_PERSON_KEY = "idUno"
const val PROFILE_ID_KEY = "idIndividuo"
const val FAMILY_ID_KEY = "idFamiglia"
const val PIVOT_ID_KEY = "idPerno"
const val CHILD_FAM_ID_KEY = "idFamFiglio"
const val SPOUSE_FAM_ID_KEY = "idFamSposo"
const val FRAGMENT_KEY = "frammento"
const val NEW_RELATIVE_KEY = "nuovo"
const val OPEN_TREE_AUTOMATICALLY_KEY = "apriAlberoAutomaticamente"
const val AUTO_OPENED_TREE_KEY = "autoOpenedTree"
const val CONSUMED_NOTIFICATIONS_KEY = "consumedNotifications"
const val GENERATION_KEY = "gen"
const val TREE_ID_KEY = "idAlbero"

const val TREE_ID_KEY_ENGLISH = "treeId"

const val TREE_2_ID_KEY = "idAlbero2"
const val DATA_ID_KEY = "idData"
const val ID_KEY = "id"
const val POSITION_KEY = "posizione"
const val RELATIONSHIP_ID_KEY = "relazione"
const val PATH_KEY = "path"
const val URI_KEY = "uri"
const val CARD_KEY = "scheda"
const val CITATION_KEY = "citaz"
const val LOCATION_KEY = "collocazione"
const val PEOPLE_LIST_CHOOSE_RELATIVE_KEY = "anagrafeScegliParente"

const val NEW_PERSON_VALUE = "TIZIO_NUOVO"
const val NEW_FAMILY_OF_VALUE = "NUOVA_FAMIGLIA_DI"
const val EXISTING_FAMILY_VALUE = "FAMIGLIA_ESISTENTE"

const val CACHE_EXTENSION_KEY = "cache"
const val PASSED_EXTENSION_KEY = "passed"
const val ZONE_EXTENSION_KEY = "zone"

const val TITLE_KEY = "title"
const val SOURCE_ID_KEY = "idFonte"
const val TEXT_KEY = "text"
const val INDI_ID_KEY = "indiId"
const val NOTE_ID_KEY = "noteId"
const val REPO_ID_KEY = "repoId"
const val SOURCE_EXTENSION_KEY =
    "fonti" //TODO shouldn't translate? would remove backwards compatibility with old versions of FamilyGem trees.
const val FROM_NOTES_KEY = "fromNotes"
const val RELATIVE_ID_KEY = "idParente"
const val SOURCE_ID_KEY_ENGLISH = "sourceId"

const val GALLERY_CHOOSE_MEDIA_KEY = "galleriaScegliMedia"
const val MEDIA_ID_KEY = "mediaId"
const val IS_ALONE_KEY = "daSolo"
const val LIBRARY_CHOOSE_SOURCE_KEY = "bibliotecaScegliFonte"