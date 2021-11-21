package com.example.kotlinstudy.week14

import com.example.kotlinstudy.coroutines.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select

fun CoroutineScope.switchMapDeferreds(input: ReceiveChannel<Deferred<String>>) = produce<String> {
    var current = input.receive() // start with first received deferred value
    while (isActive) { // loop while not cancelled/closed
        val next = select<Deferred<String>?> { // return next deferred value from this select or null
            input.onReceiveCatching { update ->
                val deferred = update.getOrNull()
                val value = deferred?.await()!!
                log("update $value")
                send(value) // send value that current deferred has produced
                input.receiveCatching().getOrNull()
            }
            current.onAwait { value ->
                log("onAwait $value")
                send(value) // send value that current deferred has produced
                input.receiveCatching().getOrNull() // and use the next deferred from the input channel
            }
        }
        if (next == null) {
            log("Channel was closed")
            break // out of loop
        } else {
            current = next
        }
    }
}

fun CoroutineScope.asyncString(str: String, time: Long) = async {
    log("delay $time $str")
    delay(time)
    log("delay end $time $str")
    str
}

fun main() = runBlocking<Unit> {
    val chan = Channel<Deferred<String>>() // the channel for test
    launch { // launch printing coroutine
        for (s in switchMapDeferreds(chan))
            log("====== $s =====") // print each received string
    }
    log("send BEGIN")
    chan.send(asyncString("BEGIN", 100))
    log("delay 100")
    delay(100) // enough time for "BEGIN" to be produced
    log("send SLOW")
    chan.send(asyncString("Slow", 500))
    log("delay 100")
    delay(100) // not enough time to produce slow
    log("send REPLACE")
    chan.send(asyncString("Replace", 100))
    log("delay 500")
    delay(500) // give it time before the last one
    log("send END")
    chan.send(asyncString("END", 500))
    log("delay 1000")
    delay(1000) // give it time to process
    log("send CLOSED")
    chan.close() // close the channel ...
    log("delay 500")
    delay(500) // and wait some time to let it finish
}
