// !LANGUAGE: +NewInference +FunctionalInterfaceConversion +SamConversionPerArgument +SamConversionForKotlinFunctions
// WITH_RUNTIME
// SKIP_DCE_DRIVEN

fun interface KRunnable {
    fun invoke()
}

fun runTwice(r: KRunnable) {
    r.invoke()
    r.invoke()
}

class A() {
    fun f() {}
}

fun box(): String {
    var x = 0
    runTwice({ x++; A() }()::f)
    if (x != 1) return "Fail"
    return "OK"
}
