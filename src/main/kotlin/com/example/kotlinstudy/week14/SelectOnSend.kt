package com.example.kotlinstudy.week14

import com.example.kotlinstudy.coroutines.log
import com.example.kotlinstudy.temp_woojin.sleep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select

fun CoroutineScope.produceNumbers(channels: List<SendChannel<Int>>) = produce<Int> {
    for (num in 1..10) { // produce 10 numbers from 1 to 10
        log("delay 100ms $num")
//        delay(100) // every 100 ms
        select<Unit> {
            onSend(num) {} // Send to the primary channel
            channels.forEach {
                it.onSend(num) {} // or to the side channel
            }
        }
    }
}

fun main() = runBlocking<Unit> {
    val channels = arrayListOf<Channel<Int>>()
    for (i in 1..10) {
        val side = Channel<Int>()
        channels.add(side)
        launch { // this is a very fast consumer for the side channel
            side.consumeEach {
                log("$i Side channel has $it")
//                sleep(i * 1000L)
            }
        }
    }

    produceNumbers(channels).consumeEach {
        log("Consuming $it")
        sleep(250) // let us digest the consumed number properly, do not hurry
    }
    log("Done consuming")
    coroutineContext.cancelChildren()
}
