package app.familygem.constant

import org.folg.gedcom.model.Person

enum class Gender {
    NONE,  // No SEX tag
    MALE,  // 'SEX M'
    FEMALE,  // 'SEX F'
    UNKNOWN,  // 'SEX U'
    OTHER;

    companion object {
        // Some other value
        /**
         * Finds the gender of [person]
         */
		@JvmStatic
		fun getGender(person: Person): Gender {
            for (fact in person.eventsFacts) {
                if (fact.tag == "SEX") {
                    return if (fact.value == null) OTHER // There is 'SEX' tag but the value is empty
                    else {
                        when (fact.value) {
                            "M" -> MALE
                            "F" -> FEMALE
                            "U" -> UNKNOWN
                            else -> OTHER
                        }
                    }
                }
            }
            return NONE // There is no 'SEX' tag
        }

        @JvmStatic
		fun isMale(person: Person) = getGender(person) == MALE

        @JvmStatic
		fun isFemale(person: Person) = getGender(person) == FEMALE
    }
}