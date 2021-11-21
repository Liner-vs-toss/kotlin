package com.example.kotlinstudy.week14

import com.example.kotlinstudy.coroutines.log
import com.example.kotlinstudy.temp_woojin.sleep
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select

val theadPool = newFixedThreadPoolContext(100, "Global")
fun CoroutineScope.globalAsyncString(time: Int) = GlobalScope.async(theadPool) {
    sleep(time.toLong())
    "Waited for $time ms"
}

fun CoroutineScope.asyncString(time: Int) = async {
    sleep(time.toLong())
    "Waited for $time ms"
}

fun CoroutineScope.asyncStringJob(time: Int) = launch {
    sleep(time.toLong())
    "Waited for $time ms"
}

fun CoroutineScope.globalAsyncStringsList(): MutableList<Deferred<String>> {
    val random = Random(3)
    return MutableList(12) { globalAsyncString(random.nextInt(10000)) }
}

fun CoroutineScope.asyncStringsList(): MutableList<Deferred<String>> {
    val random = Random(3)
    return MutableList(12) { asyncString(random.nextInt(10000)) }
}

fun CoroutineScope.asyncStringsJobList(): MutableList<Job> {
    val random = Random(3)
    return MutableList(12) { asyncString(random.nextInt(10000)) }
}

fun main(){
    log("main start")

//    fromSelect()
//    fromAwaitAll()
    fromGlobalSelect()

    log("main end")
}

private fun fromGlobalSelect() {
    runBlocking<Unit>(Dispatchers.IO) {
        val list = globalAsyncStringsList()
        val answer = mutableListOf<String>()
        repeat(3) {
            val result = select<String> {
                list.withIndex().forEach { (index, deferred) ->
                    deferred.onAwait { answer ->
                        list.removeAt(index)
                        "Deferred $index produced answer '$answer'"
                    }
                }
            }
            log(result)
            val countActive = list.count { it.isActive }
            log("$countActive coroutines are still active")
            answer.add(result)
        }
        list.filter {
            it.isActive
        }.forEach {
            log("active : ${it.isActive}")
            it.cancel("불필요하기에 취소")
            log("cancel : ${it.isCancelled}")
        }
        answer.forEach {
            log("answer : $it")
        }
    }
}

private fun fromAwaitAll() {
    runBlocking(Dispatchers.IO) {
        val list = asyncStringsList()

        val answers = awaitAll(*list.toTypedArray())
        answers.forEach {
            log(it)
        }
    }
}

private fun fromSelect() {
    runBlocking<Unit>(Dispatchers.IO) {
        val list = asyncStringsList()
        val answer = mutableListOf<String>()
        repeat(3) {
            val result = select<String> {
                list.withIndex().forEach { (index, deferred) ->
                    deferred.onAwait { answer ->
                        list.removeAt(index)
                        "Deferred $index produced answer '$answer'"
                    }
                }
            }
            log(result)
            val countActive = list.count { it.isActive }
            log("$countActive coroutines are still active")
            answer.add(result)
        }
        answer.forEach {
            log("answer : $it")
        }
    }
}
