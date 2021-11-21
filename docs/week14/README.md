# Select expression
목적
- await를 동시에 여러개를 실행 시킬 수 있고, 그 중 가장 처음으로 완료되는 것을 고를 수 있는 기능

## Selecting from channels

```kotlin
fun CoroutineScope.fizz() = produce<String> {
    while (true) { // sends "Fizz" every 300 ms
        log("delay 300ms")
        delay(300)
        send("Fizz")
    }
}

fun CoroutineScope.buzz() = produce<String> {
    while (true) { // sends "Buzz!" every 500 ms
        log("delay 100ms")
        delay(100)
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

fun main() = runBlocking<Unit> {
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

/*
16:39:18.150:Thread[main,5,main]: repeat 0
16:39:18.174:Thread[main,5,main]: delay 300ms
16:39:18.178:Thread[main,5,main]: delay 100ms
16:39:18.281:Thread[main,5,main]: delay 100ms
16:39:18.287:Thread[main,5,main]: buzz -> 'Buzz!'
16:39:18.287:Thread[main,5,main]: repeat 1
16:39:18.383:Thread[main,5,main]: delay 100ms
16:39:18.383:Thread[main,5,main]: buzz -> 'Buzz!'
16:39:18.384:Thread[main,5,main]: repeat 2
16:39:18.479:Thread[main,5,main]: delay 300ms
16:39:18.480:Thread[main,5,main]: fizz -> 'Fizz'
16:39:18.480:Thread[main,5,main]: repeat 3
16:39:18.484:Thread[main,5,main]: delay 100ms
16:39:18.484:Thread[main,5,main]: buzz -> 'Buzz!'
16:39:18.484:Thread[main,5,main]: repeat 4
16:39:18.587:Thread[main,5,main]: delay 100ms
16:39:18.588:Thread[main,5,main]: buzz -> 'Buzz!'
16:39:18.588:Thread[main,5,main]: repeat 5
16:39:18.688:Thread[main,5,main]: delay 100ms
16:39:18.689:Thread[main,5,main]: buzz -> 'Buzz!'
16:39:18.689:Thread[main,5,main]: repeat 6
16:39:18.784:Thread[main,5,main]: delay 300ms
16:39:18.785:Thread[main,5,main]: fizz -> 'Fizz'
 */
```
- producer에 `onReceive`는 select에서만 사용 가능한 확장 함수
- producer에 receive와 onReceive에 같은 점은 blocking 상태라 데이터가 올때까지 기다리는건 같으나, select는 2개 이상에 channel에서 데이터를 습득할 때 유용하게 사용 가능
- 


# Selecting on close﻿

```kotlin
suspend fun selectAorB(a: ReceiveChannel<String>, b: ReceiveChannel<String>): String =
    select<String> {
        a.onReceiveCatching { it ->
            val value = it.getOrNull()
            if (value != null) {
                "a -> '$value'"
            } else {
                "Channel 'a' is closed"
            }
        }
        b.onReceiveCatching { it ->
            val value = it.getOrNull()
            if (value != null) {
                "b -> '$value'"
            } else {
                "Channel 'b' is closed"
            }
        }
    }

fun main() = runBlocking<Unit> {
    val a = produce<String> {
        repeat(4) { send("Hello $it") }
    }
    val b = produce<String> {
        repeat(4) { send("World $it") }
    }
    repeat(8) { // print first eight results
        log(selectAorB(a, b))
    }
    coroutineContext.cancelChildren()
}
/*
17:03:35.355:Thread[main,5,main]: a -> 'Hello 0'
17:03:35.378:Thread[main,5,main]: a -> 'Hello 1'
17:03:35.378:Thread[main,5,main]: b -> 'World 0'
17:03:35.379:Thread[main,5,main]: a -> 'Hello 2'
17:03:35.379:Thread[main,5,main]: a -> 'Hello 3'
17:03:35.379:Thread[main,5,main]: b -> 'World 1'
17:03:35.380:Thread[main,5,main]: Channel 'a' is closed
17:03:35.380:Thread[main,5,main]: Channel 'a' is closed
 */
```
- select에 경우 channel 닫혀 있는 데이터에 대해서도 데이터를 가져올 수 있고, 닫혀 있는 채널을 receive한 경우 에러가 발생한다.
- `onReceiveCatching` 을 이용하면 에러가 발생하지 않고, `ChannelResult`에 담겨 있는 데이터를 가져올 수 있음
- select에서는 먼저 등록된 channel에 대해서 우선순위를 두는 특징이 있음
- close된 채널에 대해서 먼저 읽는 특성이 존재
- close된 채널이 2개인 경우, 먼저 등록된 채널에 우선순위가 존재

# Selecting to send
```kotlin
fun CoroutineScope.produceNumbers(channels: List<SendChannel<Int>>) = produce<Int> {
    for (num in 1..10) { // produce 10 numbers from 1 to 10
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
                delay(i * 1000L)
            }
        }
    }

    produceNumbers(channels).consumeEach {
        log("Consuming $it")
        delay(250) // let us digest the consumed number properly, do not hurry
    }
    log("Done consuming")
    coroutineContext.cancelChildren()
}
/*
18:39:29.143:Thread[main,5,main]: Consuming 1
18:39:29.168:Thread[main,5,main]: 1 Side channel has 2
18:39:29.168:Thread[main,5,main]: 2 Side channel has 3
18:39:29.168:Thread[main,5,main]: 3 Side channel has 4
18:39:29.168:Thread[main,5,main]: 4 Side channel has 5
18:39:29.169:Thread[main,5,main]: 5 Side channel has 6
18:39:29.169:Thread[main,5,main]: 6 Side channel has 7
18:39:29.169:Thread[main,5,main]: 7 Side channel has 8
18:39:29.169:Thread[main,5,main]: 8 Side channel has 9
18:39:29.169:Thread[main,5,main]: 9 Side channel has 10
18:39:29.416:Thread[main,5,main]: Done consuming
 */
```
- onSend를 이용하면 여러 채널에 대해서 가장 여유 있는 채널 1곳에만 데이터를 전송할 수 있는 기능 제공
- 생각보다 조건이 까다로움 consumeEach쪽에 sleep을 사용하면 send를 하더라도 전부 데이터를 읽지 않음

# Selecting deferred values﻿

```kotlin
fun CoroutineScope.asyncString(time: Int) = async {
    sleep(time.toLong())
    "Waited for $time ms"
}

fun CoroutineScope.asyncStringsList(): MutableList<Deferred<String>> {
    val random = Random(3)
    return MutableList(12) { asyncString(random.nextInt(1000)) }
}

fun main() = runBlocking<Unit>(Dispatchers.IO) {
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
/*
19:04:17.745:Thread[DefaultDispatcher-worker-3,5,main]: sleep start 734
19:04:17.745:Thread[DefaultDispatcher-worker-11,5,main]: sleep start 961
19:04:17.745:Thread[DefaultDispatcher-worker-13,5,main]: sleep start 459
19:04:17.744:Thread[DefaultDispatcher-worker-2,5,main]: sleep start 660
19:04:17.745:Thread[DefaultDispatcher-worker-7,5,main]: sleep start 128
19:04:17.745:Thread[DefaultDispatcher-worker-4,5,main]: sleep start 210
19:04:17.744:Thread[DefaultDispatcher-worker-5,5,main]: sleep start 564
19:04:17.745:Thread[DefaultDispatcher-worker-6,5,main]: sleep start 581
19:04:17.744:Thread[DefaultDispatcher-worker-10,5,main]: sleep start 882
19:04:17.743:Thread[DefaultDispatcher-worker-8,5,main]: sleep start 202
19:04:17.745:Thread[DefaultDispatcher-worker-12,5,main]: sleep start 585
19:04:17.745:Thread[DefaultDispatcher-worker-9,5,main]: sleep start 549
19:04:17.913:Thread[DefaultDispatcher-worker-7,5,main]: Deferred 4 produced answer 'Waited for 128 ms'
19:04:17.915:Thread[DefaultDispatcher-worker-7,5,main]: 11 coroutines are still active
19:04:17.975:Thread[DefaultDispatcher-worker-8,5,main]: Deferred 4 produced answer 'Waited for 202 ms'
19:04:17.975:Thread[DefaultDispatcher-worker-8,5,main]: 10 coroutines are still active
19:04:17.986:Thread[DefaultDispatcher-worker-4,5,main]: Deferred 2 produced answer 'Waited for 210 ms'
19:04:17.986:Thread[DefaultDispatcher-worker-4,5,main]: 9 coroutines are still active
19:04:17.990:Thread[DefaultDispatcher-worker-4,5,main]: answer : Deferred 4 produced answer 'Waited for 128 ms'
19:04:17.990:Thread[DefaultDispatcher-worker-4,5,main]: answer : Deferred 4 produced answer 'Waited for 202 ms'
19:04:17.990:Thread[DefaultDispatcher-worker-4,5,main]: answer : Deferred 2 produced answer 'Waited for 210 ms'
 */
```
- select를 deferred에도 적용 가능하며, IO 작업의 경우는 ThreadPool을 갖고 있는 Dispatcher를 사용해야 병렬로 동작 한다
- 가장 빠른 Deferred 하나를 가져와서 먼저 처리가 가능하나, IO 작업인 경우 전체 Deferred가 처리될 때까지 기다려야 한다
- 어떻게 보면 awaitAll과 별 다르지 않으나, 다른 부분은 먼저 처리된 데이터를 받아서 가공을 미리 할 수 있는 점이 다르다
- 단, GlobalScope를 사용해 async를 이용한 경우는 block되지 않는다.

# Switch over a channel of deferred values﻿
```kotlin
fun CoroutineScope.switchMapDeferreds(input: ReceiveChannel<Deferred<String>>) = produce<String> {
    var current = input.receive() // start with first received deferred value
    while (isActive) { // loop while not cancelled/closed
        val next = select<Deferred<String>?> { // return next deferred value from this select or null
            input.onReceiveCatching { update ->
                update.getOrNull()
            }
            current.onAwait { value ->
                send(value) // send value that current deferred has produced
                input.receiveCatching().getOrNull() // and use the next deferred from the input channel
            }
        }
        if (next == null) {
            println("Channel was closed")
            break // out of loop
        } else {
            current = next
        }
    }
}

fun CoroutineScope.asyncString(str: String, time: Long) = async {
    delay(time)
    str
}

fun main() = runBlocking<Unit> {
    val chan = Channel<Deferred<String>>() // the channel for test
    launch { // launch printing coroutine
        for (s in switchMapDeferreds(chan)) 
            println(s) // print each received string
    }
    chan.send(asyncString("BEGIN", 100))
    delay(200) // enough time for "BEGIN" to be produced
    chan.send(asyncString("Slow", 500))
    delay(100) // not enough time to produce slow
    chan.send(asyncString("Replace", 100))
    delay(500) // give it time before the last one
    chan.send(asyncString("END", 500))
    delay(1000) // give it time to process
    chan.close() // close the channel ... 
    delay(500) // and wait some time to let it finish
}

/*
BEGIN
Replace
END
Channel was closed
 */
```
- channel에 deferred를 consume하게 할 때 producer가 전달한 데이터의 순서가 꼬일 경우 데이터가 안보내지는 경우가 존재하니 주의가 필요
- BEGIN -> SLOW -> REPLACE -> END 순으로 보내긴 하지만, SLOW가 느려서 REPLACE가 먼저 보내지고, SLOW가 보내지는데, SLOW는 순서상 안맞기 때문에 무시되어 버린다 


# 과제
- 신분증 인증을 진행할 때, 금융결제원, 행정안전부에 인증을 진행해야 함
- 금융 결제원에서 응답 시간은 1~500ms가 소요되고, 행정 안전부는 1~3000ms가 소요될 수 있음
- 둘 중 한개의 인증만 성공하면 성공으로 판단되게 작성 필요
- ex) 금융결제원에서 300ms에 성공 응답을 준 경우 금융결제원 인증 성공 로그를 찍고, 행정 안전부의 검증은 확인하지 않아도 됨

