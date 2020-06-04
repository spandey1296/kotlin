// !DIAGNOSTICS: -UNCHECKED_CAST -EXPERIMENTAL_API_USAGE_ERROR -UNUSED_PARAMETER

class Bar

fun <T> materialize() = null as T

interface FlowCollector<in T> {
    suspend fun emit(value: T)
}

interface Flow<T>

public fun <T> flow(@BuilderInference block: suspend FlowCollector<T>.() -> Unit) = materialize<Flow<T>>()

suspend fun foo(x: Int) = flow {
    var y = 1
    y += if (x > 2) 1 else 2
    emit(materialize<Bar>())
}
