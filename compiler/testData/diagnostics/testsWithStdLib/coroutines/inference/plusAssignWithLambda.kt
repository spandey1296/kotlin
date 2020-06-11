// !DIAGNOSTICS: -UNUSED_ANONYMOUS_PARAMETER -UNUSED_PARAMETER

fun foo(total: Int, next: Int) = 10
fun foo(total: Int, next: Float) = 10
fun foo(total: Float, next: Int) = 10

fun foo(x: Float, y: Float) = {
    var newValue = 1
    // We analyze lambda once after the fix
    newValue += listOf<Int>().asSequence().fold(0) { total, next -> total + next }
    newValue += listOf<Int>().asSequence().fold(0, fun(total, next): Int { return total + next })
    newValue += listOf<Int>().asSequence().fold(0, fun(total, next) = total + next)
    newValue += listOf<Int>().asSequence().fold(0, ::foo)
}
