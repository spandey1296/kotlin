// WITH_RUNTIME
// IGNORE_BACKEND: JVM_IR

import kotlin.contracts.*
import kotlin.coroutines.*
import kotlin.experimental.*

interface CoroutineScope

@OptIn(ExperimentalTypeInference::class)
public fun <E> CoroutineScope.produce(
    @BuilderInference block: suspend ProducerScope<E>.() -> Unit
): ReceiveChannel<E>  = TODO()

interface ProducerScope<in E> : CoroutineScope, SendChannel<E> {
    public val channel: SendChannel<E>
}

interface ReceiveChannel<out E>
interface SelectInstance<in R>
interface SelectBuilder<in R> {
    operator fun <P, Q> SelectClause2<P, Q>.invoke(param: P, block: suspend (Q) -> R)
}
interface SelectClause2<in P, out Q> {
    public fun <R> registerSelectClause2(select: SelectInstance<R>, param: P, block: suspend (Q) -> R)
}
interface SendChannel<in E> {
    val onSend: SelectClause2<E, SendChannel<E>>
}
suspend fun delay(timeMillis: Long): Unit {}
@ExperimentalContracts
suspend inline fun <R> select(crossinline builder: SelectBuilder<R>.() -> Unit): R {
    contract {
        callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
    }
    TODO()
}

@ExperimentalContracts
fun CoroutineScope.produceNumbers(side: SendChannel<Int>) = produce<Int> {
    for (num in 1..10) {
        delay(100)
        select<Unit> {
            onSend(num) {}
            side.onSend(num) {}
        }
    }
}