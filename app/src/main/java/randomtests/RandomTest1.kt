package randomtests

fun main() {
    var extension = arrayOf("jpg").first()
    if (extension == "jpeg" || extension !in arrayOf("png", "gif", "bmp", "jpg")) extension = "jpg" //TODO why should the default be jpg??
    println(extension)
}