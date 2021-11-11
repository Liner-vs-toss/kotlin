## [Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html#your-first-coroutine)

### 공부를 통해 얻고자 하는 것
- 코루틴이 무엇인가?
- 코루틴과 스레드의 차이점은?
- blocking 과 non-blocking의 차이점은?

### Your first coroutine
- 코루틴은 일시 정지 가능하다. 개념적으로 코드를 동시에 실행해야 한다는 의미에서 스레드와 비슷하다.
- 하지만 코루틴의 동작을 특정 스레드로 한정하지 않는다. 코루틴은 특정 스레드에서 실행이 중단되고 다른 스레드에서 재개될 수 있다.
- 코루틴을 경량 스레드라고 볼 수 있지만, 실제로 많은 차이가 있다. (**어떤 차이점이 있는지 이해하는 것이 중요**)

```kotlin
fun main() = runBlocking {   // coroutine scope. blocking
    // main coroutine
    launch {
        // launch coroutine
        delay(1000L)    // suspending function. non-blocking
        println("World!")
    }

    println("Hello")
}
```

### Structured concurrency
- 코루틴은 Coroutine Scope 안에서만 실행 가능하다.
- 부모 Scope는 자식 코루틴이 종료될 때까지 수행을 완료할 수 없다.

### Suspending function
```kotlin
fun main() = runBlocking {
    launch { doWorld() }
    println("Hello")
}

suspend fun doWorld() {
    delay(1000) // suspend function 안에서 코루틴 빌더 실행
    println("World!")
}
```

### Scope builder
- 코루틴 Scope를 생성하는 빌더
- `runBlocking`, `coroutineScope`
    - 공통점 : `runBlocking`, `coroutineScope` 모두 자식 코루틴이 종료될 때까지 대기한다.
    - 차이점 :
        - `runBlocking`은 현재 스레드를 block 시킨다. regular function
        - `coroutineScope`는 일시 정지 시킨다. suspending function

### Job
- `launch` 코루틴 빌더는 `Job`을 반환한다. `join()` 메소드로 코루틴 실행이 완료될 때까지 기다린다.
```kotlin
fun main() = runBlocking {
    val job = launch {
        delay(1000)
        println("World!")
    }

    println("Hello")
    job.join()  // launch 코루틴이 종료될 때까지 대기
    println("Done")
}
```

### light-weight
```kotlin
fun executeCoroutine() = runBlocking {
    repeat(10_000) {
        launch {
            delay(1000)
            println("Coroutine")
        }
    }
}

fun executeThread() {
    repeat(10_000) {
        thread {
            Thread.sleep(1000)
            println("Thread")
        }
  }
}
```

### 정리
- 코루틴이 무엇인가?
    - 코루틴은 코드를 동시에 처리하기 위한 개념이다.
    - 일시정지, 재개를 반복하며 여러 코루틴이 동시에 실행된다.
- 코루틴과 스레드의 차이점은?
    - 코투린의 실행은 특정 스레드에 국한되지 않는다. 코루틴 실행은 일시정지 되었다가 다른 스레드에서 재개될 수 있다.
- blocking 과 non-blocking의 차이점은? [IBM asynchronous I/O](https://developer.ibm.com/articles/l-async/)
    - blocking(제어권) : 호출된 함수가 작업이 완료될 때까지 제어권을 가짐.
    - non-blocking(제어권) :  호출된 함수가 작업이 완료되지 않았더라도 제어권을 바로 반환해, 호출한 곳에서 수행을 이어감.
    - sync(동시성) :  호출된 함수의 수행 결과를 호출한 곳에서 처리.
    - async(동시성) : 호출된 함수의 수행 결과를 호출된 곳에서만 처리. (호출한 곳에서는 알지 못함)
- 동시성(Concurrency)과 병렬성(Parallelism) [Coroutine, Thread 와의 차이점](https://aaronryu.github.io/2019/05/27/coroutine-and-thread/)
    - 병렬은 동시성을 의미하지만 동시성은 병렬성이 없이도 발생할 수 있다.
    - 동시성 : 동시성은 정확히 같은 시점에 실행되는지 여부와는 상관이 없다.
    - 병렬성 : 병렬 실행은 두 스레드가 정확히 같은 시점에 실행될 때만 발생한다. (두 개 이상의 스레드가 필요하다)


## [Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)

### 공부를 통해 얻고자 하는 것
- 코루틴 실행 취소 / 타임아웃 시 예외처리 방법에 대해 알 수 있다.

![코루틴 라이프사이클](./image/lifecycle.png)

## Cancelling coroutine exception
- 실행중인 코루틴을 취소할 수 있다.
- `Job` object로 실행중인 코루틴 취소
```kotlin
fun main() = runBlocking {
    val job = launch {
        repeat(1000) { i ->
            println("[job] $i")
            delay(500)
        }
    }

    delay(1000)
    println("[main] waiting")
    job.cancel()    // 실행중인 코루틴 취소
    job.join()
    println("[main] finished")
}
```

- 아래 예제에서는 Job을 취소해도 코루틴 취소를 확인하지 않기 때문에 5번의 반복이 모두 실행된 후에 코루틴이 종료된다.
```kotlin
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while ( i < 5) {    // cancel() 이후에도 수행된다. isActive 로 코루틴 실행 확인
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("[job] ${i++}")
                nextPrintTime += 500L
            }
        }
    }

    delay(1000)
    println("[main] waiting")
    job.cancelAndJoin()
    println("[main] finished")
}
```

- 코루틴이 취소된 후에 종료 작업을 실행한다. (finally)
```kotlin
fun main() = runBlocking {
    val job = launch {
        try {
            repeat(100) { i ->
                println("[job] $i")
                delay(500)
            }
        } catch (e: CancellationException) {
            println("exception")
        } finally {
            println("canceled")
        }
    }

    delay(1000)
    println("[main] waiting")
    job.cancelAndJoin()
    println("[main] finished")
}
```

- 취소된 코루틴을 일시정지 하고 싶을 때 `withContext`를 활용할 수 있다.
```kotlin
val job = launch {
    try {
        repeat(1000) { i ->
            println("job: I'm sleeping $i ...")
            delay(500L)
        }
    } finally {
        withContext(NonCancellable) {
            println("job: I'm running finally")
            delay(1000L)
            println("job: And I've just delayed for 1 sec because I'm non-cancellable")
        }
    }
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")
```

### Timeout
- 코루틴 수행이 일정 시간을 초과하는 경우 timeout을 발생시킬 수 있다.
- `withTimeout`으로 지정한 시간을 초과하면 `TimeoutCancellationException`을 발생시킨다.
```kotlin
fun main() = runBlocking {
    val result = withTimeout(3000) {    // withTimeoutNull : 타임아웃 발생 시 null 반환
        repeat(1000) { i ->
            println("[job] $i")
            delay(100)
        }
    }
    println(result)
}
```

- `Keep this in mind if you open or acquire some resource inside the block that needs closing or release outside of the block.`
```kotlin
fun main() {
    runBlocking {
        repeat(10_000) { i -> // 100,000 코루틴
            launch {
                var resource: Resource? = null
                try {
                    // launch 코루틴 안에서 TimeoutCancellationException 발생
                    withTimeout(25) {    // Timeout 30 ms
                        delay(10)
                        resource = Resource()
                    }
                } finally {
                    // TimeoutCancellationException 발생 후 종료 작업에서 close 처리
                    resource.close()    // release Resource
                }
            }
        }
    }

    // runBlocking 완료
    println(acquired)
}
```
