// IS_APPLICABLE: false
// WITH_RUNTIME
fun test(list: List<String>) {
    list.forEach { item ->
        // comment
        println(item)
    }<caret>
}