// !DIAGNOSTICS: -UNUSED_ANONYMOUS_PARAMETER

fun main() {
    var newValue = 1
    // We analyze lambda once after the fix
    newValue += listOf<Int>().asSequence().fold(0) { total, next -> total + next }
}
