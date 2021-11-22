package com.example.kotlinstudy.week14

import com.example.kotlinstudy.coroutines.log
import com.example.kotlinstudy.temp_woojin.sleep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select

fun CoroutineScope.fizz() = produce<String> {
    while (true) { // sends "Fizz" every 300 ms
        log("delay 300ms")
        sleep(300)
        send("Fizz")
    }
}

fun CoroutineScope.buzz() = produce<String> {
    while (true) { // sends "Buzz!" every 500 ms
        log("delay 100ms")
        sleep(100)
        send("Buzz!")
    }
}

suspend fun selectFizzBuzz(fizz: ReceiveChannel<String>, buzz: ReceiveChannel<String>) {
    select<Unit> { // <Unit> means that this select expression does not produce any result
        fizz.onReceive { value ->  // this is the first select clause
            log("fizz -> '$value'")
        }
        buzz.onReceive { value ->  // this is the second select clause
            log("buzz -> '$value'")
        }
    }
}

fun main() = runBlocking<Unit>(Dispatchers.IO) {
    val fizz = fizz()
    val buzz = buzz()
    repeat(7) {
        log("repeat $it")
        selectFizzBuzz(fizz, buzz)
//        log("fizz -> '${fizz.receive()}'")
//        log("buzz -> '${buzz.receive()}'")
    }
    coroutineContext.cancelChildren() // cancel fizz & buzz coroutines
}
