@file:Suppress("MagicNumber")

package com.badoo.reaktive.observable

import com.badoo.reaktive.disposable.CompositeDisposable
import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.disposable.plusAssign
import com.badoo.reaktive.utils.SharedList
import com.badoo.reaktive.utils.atomic.AtomicList
import com.badoo.reaktive.utils.atomic.update
import com.badoo.reaktive.utils.replace
import com.badoo.reaktive.utils.serializer.serializer

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by source [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-java.lang.Iterable-io.reactivex.functions.Function-).
 */
@Suppress("ComplexMethod", "LongMethod")
fun <T, R> Iterable<Observable<T>>.zip(mapper: (List<T>) -> R): Observable<R> =
    observable { emitter ->
        val sources = toList()

        if (sources.isEmpty()) {
            emitter.onComplete()
            return@observable
        }

        val disposables = CompositeDisposable()
        emitter.setDisposable(disposables)

        val values = SharedList<SharedList<T>>(sources.size) { SharedList() }
        val completed = AtomicList(List(sources.size) { false })

        val serializer =
            serializer<ZipEvent<T>> { event ->
                when (event) {
                    is ZipEvent.OnNext -> {
                        var readyValues: List<T>? = null

                        values[event.index].add(event.value)
                        if (values.all(List<*>::isNotEmpty)) {
                            readyValues = values.map { it[0] }
                            values.forEach { it.removeAt(0) }
                        }

                        if (readyValues == null) {
                            return@serializer true
                        }

                        val result =
                            try {
                                mapper(readyValues)
                            } catch (e: Throwable) {
                                emitter.onError(e)
                                return@serializer false
                            }

                        emitter.onNext(result)

                        // Complete if for any completed source there are no values left in the queue
                        values.forEachIndexed { index, queue ->
                            if (queue.isEmpty() && completed.value[index]) {
                                emitter.onComplete()
                                return@serializer false
                            }
                        }

                        true
                    }

                    is ZipEvent.OnComplete -> {
                        completed.update {
                            it.replace(event.index, true)
                        }

                        // Complete if a source is completed and no values left in its queue
                        val isEmpty = values[event.index].isEmpty()
                        if (isEmpty) {
                            emitter.onComplete()
                        }

                        !isEmpty
                    }

                    is ZipEvent.OnError -> {
                        emitter.onError(event.error)
                        false
                    }
                }
            }

        sources.forEachIndexed { index, source ->
            source.subscribe(
                object : ObservableObserver<T> {
                    override fun onSubscribe(disposable: Disposable) {
                        disposables += disposable
                    }

                    override fun onNext(value: T) {
                        serializer.accept(ZipEvent.OnNext(index, value))
                    }

                    override fun onComplete() {
                        serializer.accept(ZipEvent.OnComplete(index))
                    }

                    override fun onError(error: Throwable) {
                        serializer.accept(ZipEvent.OnError(error))
                    }
                }
            )
        }
    }

private sealed class ZipEvent<out T> {

    class OnNext<out T>(val index: Int, val value: T) : ZipEvent<T>()
    class OnComplete(val index: Int) : ZipEvent<Nothing>()
    class OnError(val error: Throwable) : ZipEvent<Nothing>()
}

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by [sources][sources] [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zipArray-io.reactivex.functions.Function-boolean-int-io.reactivex.ObservableSource...-).
 */
fun <T, R> zip(vararg sources: Observable<T>, mapper: (List<T>) -> R): Observable<R> =
    sources
        .asList()
        .zip(mapper)

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.BiFunction-).
 */
fun <T1, T2, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    mapper: (T1, T2) -> R
): Observable<R> =
    listOf(source1, source2)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(values[0] as T1, values[1] as T2)
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function3-).
 */
fun <T1, T2, T3, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    mapper: (T1, T2, T3) -> R
): Observable<R> =
    listOf(source1, source2, source3)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(values[0] as T1, values[1] as T2, values[2] as T3)
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function4-).
 */
fun <T1, T2, T3, T4, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    mapper: (T1, T2, T3, T4) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4)
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function5-).
 */
fun <T1, T2, T3, T4, T5, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    source5: Observable<T5>,
    mapper: (T1, T2, T3, T4, T5) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4, source5)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5)
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function6-).
 */
fun <T1, T2, T3, T4, T5, T6, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    source5: Observable<T5>,
    source6: Observable<T6>,
    mapper: (T1, T2, T3, T4, T5, T6) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4, source5, source6)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5, values[5] as T6)
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function7-).
 */
fun <T1, T2, T3, T4, T5, T6, T7, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    source5: Observable<T5>,
    source6: Observable<T6>,
    source7: Observable<T7>,
    mapper: (T1, T2, T3, T4, T5, T6, T7) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4, source5, source6, source7)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(
                values[0] as T1,
                values[1] as T2,
                values[2] as T3,
                values[3] as T4,
                values[4] as T5,
                values[5] as T6,
                values[6] as T7
            )
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function8-).
 */
fun <T1, T2, T3, T4, T5, T6, T7, T8, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    source5: Observable<T5>,
    source6: Observable<T6>,
    source7: Observable<T7>,
    source8: Observable<T8>,
    mapper: (T1, T2, T3, T4, T5, T6, T7, T8) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4, source5, source6, source7, source8)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(
                values[0] as T1,
                values[1] as T2,
                values[2] as T3,
                values[3] as T4,
                values[4] as T5,
                values[5] as T6,
                values[6] as T7,
                values[7] as T8
            )
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function9-).
 */
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    source5: Observable<T5>,
    source6: Observable<T6>,
    source7: Observable<T7>,
    source8: Observable<T8>,
    source9: Observable<T9>,
    mapper: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4, source5, source6, source7, source8, source9)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(
                values[0] as T1,
                values[1] as T2,
                values[2] as T3,
                values[3] as T4,
                values[4] as T5,
                values[5] as T6,
                values[6] as T7,
                values[7] as T8,
                values[8] as T9
            )
        }

/**
 * Returns an [Observable] that emits the results of the specified [mapper] function,
 * applied to combinations of elements emitted, in sequence, by the specified [Observable]s.
 *
 * Please refer to the corresponding RxJava [document](http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#zip-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.ObservableSource-io.reactivex.functions.Function9-).
 */
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> zip(
    source1: Observable<T1>,
    source2: Observable<T2>,
    source3: Observable<T3>,
    source4: Observable<T4>,
    source5: Observable<T5>,
    source6: Observable<T6>,
    source7: Observable<T7>,
    source8: Observable<T8>,
    source9: Observable<T9>,
    source10: Observable<T10>,
    mapper: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> R
): Observable<R> =
    listOf(source1, source2, source3, source4, source5, source6, source7, source8, source9, source10)
        .zip { values ->
            @Suppress("UNCHECKED_CAST")
            mapper(
                values[0] as T1,
                values[1] as T2,
                values[2] as T3,
                values[3] as T4,
                values[4] as T5,
                values[5] as T6,
                values[6] as T7,
                values[7] as T8,
                values[8] as T9,
                values[9] as T10
            )
        }
