// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_ANONYMOUS_PARAMETER

open class A

operator fun <T> T.plus(x: (T) -> Int) = A()
operator fun <T> T.plusAssign(x: (Int) -> T) {}

fun <T> id(x: T) = x

fun main() {
    var newValue = A()
    // It's abiguity because we anayze lambda once (only in `+=` context)
    // Before the fix, it was OK, but there was exponential complexity in such places
    newValue <!ASSIGN_OPERATOR_AMBIGUITY!>+=<!> id { total -> A() }
}
