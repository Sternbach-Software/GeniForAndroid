package randomtests

fun main() {
    data class x(val a: String, private val y: Boolean)
//    data class y(val b: String, private val z: Boolean)
    println(x("a", true) == x("a", false))
//    println(y("a", true) == y("a", false))
}