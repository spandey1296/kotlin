// WITH_RUNTIME
fun test(list: List<String>) {
    list.forEach { item -> /* aaa */ println(item); println(item) /* bbb */ }<caret>
}