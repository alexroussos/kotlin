
package kotlin

import java.util.concurrent.atomic.AtomicReference


private class ConstrainedOnceSequence<T>(sequence: Sequence<T>) : Sequence<T>, AtomicReference<Sequence<T>>(sequence) {

    override fun iterator(): Iterator<T> {
        val sequence = this.getAndSet(null) ?: throw IllegalStateException("This sequence can be consumed only once.")
        return sequence.iterator()
    }
}

