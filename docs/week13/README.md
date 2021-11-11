## 코루틴 공식문서. [Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html)

### 학습 목표
- 이번 세션에서는 예외 핸들링과 예외 취소에 대해 설명한다.
- 취소된 코루틴은 `CancellationException`을 발생시킨다.
- 취소 중에 예외가 발생하거나 동일 코루틴의 자식들에서 예외가 발생할 때 어떻게 되는지 알아본다.

### 예외 전파(Exception propagation)
- 코루틴 빌더는 두 가지 방법으로 예외를 전파한다.
    - 자동 예외 전파(launch, actor) : uncaught exception
    - 사용자에게 노출(exposing them to users. async, produce) : relying on user to consume the final exception
```kotlin
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
    val job = GlobalScope.launch {
      println("Throwing exception from launch")
      throw IllegalArgumentException()
    }
    // Thread.defaultUncaughtExceptionHandler가 콘솔 출력
    job.join()  // uncaught IllegalArgumentException
    println("Joined failed job")

    val deferred = GlobalScope.async {
      println("Throwing exception from async")
      throw ArithmeticException()
    }

    try {
      deferred.await()  // await 해야만 예외 전파
      println("Unreached")
    } catch (e: ArithmeticException) {
      println("catch ArithmeticException")
    }
  }
```

- 결과
```text
Throwing exception from launch
Exception in thread "DefaultDispatcher-worker-1" java.lang.IllegalArgumentException
	at 07_coroutine_exceptions_handling._01_exception_propagationKt$main$1$job$1.invokeSuspend(01_exception_propagation.kt:13)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:106)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:571)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:750)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:678)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:665)
Joined failed job
Throwing exception from async
catch ArithmeticException
```

### CoroutineExceptionHandler
- **uncaught exception을 콘솔에 출력하도록 커스텀 할 수 있다.**
- 루트 코루틴에 있는 `CoroutineExceptionHandler`를 이용해서 루트 코루틴과, 사용자 지정 예외가 발생할 수 있는 모든 자식 코루틴에 예외 catch 블록으로 사용할 수 있다.
- 일반적으로 exception handler는 **로그**를 남기거나, **에러 메시지** 출력, 애플리케이션 **종료/재시작** 에 사용된다.
- JVM에서는 `ServiceLoader`에 `CoroutineExceptionHandler`를 등록해서 모든 코루틴에 전역 exception handler를 재정의 할 수 있다.
- **`CoroutineExceptionHandler`는 uncaught exception에서만 호출된다.**
- async 빌더는 모든 예외를 catch 하고 Deferred 객체에 결과를 담기 때문에 `CoroutineExceptionHandler`가 동작하지 않는다.
```kotlin
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
    val exceptionHandler = CoroutineExceptionHandler { _/*CoroutineContext*/, exception ->
      println("CoroutineExceptionHandler got $exception")
    }

    val job = GlobalScope.launch(exceptionHandler) {
      throw AssertionError()  // CoroutineExceptionHandler 호출됨
    }
    val deferred = GlobalScope.async(exceptionHandler) {
      throw ArithmeticException() // CoroutineExceptionHandler 호출되지 않음
    }
    joinAll(job, deferred)
  }
```

- 결과
```text
CoroutineExceptionHandler got java.lang.AssertionError
```

### Cancellation and exceptions
- 코루틴이 취소될 때 `CancellationException`이 발생한다.
- `CancellationException` 예외는 모든 핸들러에서 무시된다. catch 블록에서 디버그 정보로 사용할 수 있다.
- **자식 코루틴이 취소되도 부모 코루틴은 취소되지 않는다.**
- **CancellationException 이외의 예외가 발생하면 부모 코루틴까지 취소한다.**
    - **This behaviour cannot be overridden and is used to provide stable coroutines hierarchies for structured concurrency.**
    - CoroutineExceptionHandler 구현은 자식 코루틴에 사용되지 않는다.

```kotlin
fun main() = runBlocking {
  val job = launch {  // 부모 코루틴
    val child = launch {  // 자식 코루틴
      try {
        delay(Long.MAX_VALUE)
      } catch (e: CancellationException) {
        println("Catch CancellationException")
      } finally {
        println("Child is cancelled")
      }
    }
    yield() // 일시정지
    println("Cancelling child")
    child.cancel()  // 자식 코루틴 취소
    child.join() // 자식 코루틴 종료 대기

    yield() // 일시정지
    println("Parent is not cancelled")
  }

  job.join()  // 부모 코루틴 종료 대기
}
```

- 결과 로그
```text
Cancelling child
Catch CancellationException
Child is cancelled
Parent is not cancelled
```

- original 예외는 모든 자식 코루틴이 취소되었을 때 부모 코루틴에서 처리된다.
    - (The original exception is handled by the parent only when all its children terminate)

```kotlin
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    val job = GlobalScope.launch(handler) {  // 부모 코루틴
        launch {  // 첫 번째 자식 코루틴
            try {
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    println("자식 코루틴 취소")  // 두 번째 코루틴 예외 전파
                    delay(100)
                    println("코루틴 취소된 후에도 동작하는 컨텍스트")
                }
            }
        }
        launch {  // 두 번째 자식 코루틴
            delay(10)
            println("두 번째 코루틴 예외 발생")
            throw ArithmeticException()
        }
    }
    job.join()
}
```

- 결과

```text
두 번째 코루틴 예외 발생
자식 코루틴 취소
코루틴 취소된 후에도 동작하는 컨텍스트
CoroutineExceptionHandler got java.lang.ArithmeticException
```

### Exceptions aggregation
- 다수의 자식 코루틴에서 예외가 발생했을 때, 첫 번째 예외만 핸들링 된다.
    - When multiple children of a coroutine fail with an exception, the general rule is "the first exception wins"

```
Note: This above code will work properly only on JDK7+ that supports suppressed exceptions
```

```kotlin
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }

    val job = GlobalScope.launch(handler) {
        launch {
            // 첫 번째 자식 코루틴
            try {
                delay(Long.MAX_VALUE)
            } finally {
                throw ArithmeticException() // 두 번째 자식 코루틴에서 예외가 발생된 후 throwing
            }
        }

        launch {
            // 두 번째 자식 코루틴
            delay(100)
            // 첫 번째 예외 발생
            // CoroutineExceptionHandler에서 해당 예외를 처리한다.
            throw IOException()
        }
        delay(Long.MAX_VALUE)
    }

    job.join()
}
```

- 결과
```text
CoroutineExceptionHandler got java.io.IOException
```

- `Cancellation exceptions` are transparent and are unwrapped by default
  @OptIn(DelicateCoroutinesApi::class)
  fun main() = runBlocking {
  val handler = CoroutineExceptionHandler { _, exception ->
  println("CoroutineExceptionHandler got $exception")
  }

  val job = GlobalScope.launch(handler) {  // 부모 코루틴
  val inner = launch {  // depth 1 자식 코루틴
  launch {  // depth 2 자식 코루틴
  throw IOException() // original 예외
  }
  }
  try {
  inner.join()  // job 종료 대기
  } catch (e: CancellationException) {
  println("Rethrowing CancellationException with original cause")
  // cancellation exception is rethrown
  // yet the original IOException gets to the handler
  throw e
  }
  }

  job.join()
  }

- 결과
```text
Rethrowing CancellationException with original cause
CoroutineExceptionHandler got java.io.IOException
```

### Supervision
- `cancellation`은 코루틴의 전체 계층을 통해 전파되는 양방향 관계이다.
- `supervision`은 부모 코루틴이 자식 코루틴을 제어, 관리 하는 것을 의미한다.
- 단방향 취소 사용 예
    - 프론트 : UI 컴포넌트에서 문제가 생기면 자식 코루틴의 결과가 불필요할 때.
    - 서버 : 자식 코루틴의 실행을 제어하고, 실패를 트래킹 하거나, 실패한 코루틴만 재실행 할 때
- `job` 과 `supervision job` 의 차이
    - **일반 `job`과 달리 취소가 아래쪽으로만 전파된다.**
    - **자식 코루틴의 실패가 부모 코루틴으로 전파되지 않는다.(모든 자식 코루틴이 자체적으로 예외를 핸들링 한다.)**

#### Supervision job
- 일반 `job`과 달리 취소가 아래쪽으로만 전파된다.

```kotlin
fun main() = runBlocking {
    val supervisor = SupervisorJob()
    with(CoroutineScope(coroutineContext + supervisor)) {
        val firstChild = launch(CoroutineExceptionHandler { _, _ -> }) {
            println("firstChild : 첫 번째 자식 코루틴 실패")
            throw AssertionError("첫 번째 자식 코루틴 실패")
        }

        val secondChild = launch {
            // 첫 번쨰 자식 코루틴의 실패가 두 번째 자식 코루틴에 전파되지 않는다.
            firstChild.join()
            println("secondChild : 첫 번째 자식 코루틴 취소: ${firstChild.isCancelled}")
            try {
                delay(Long.MAX_VALUE)
            } finally {
                // supervisor의 취소는 전파된다.
                println("secondChild : 두 번째 자식 코루틴 취소 by supervisor cancelled")
            }
        }

        firstChild.join()
        println("supervisor : supervisor 취소")
        supervisor.cancel()
        secondChild.join()
    }
}
```

- 결과
```text
firstChild : 첫 번째 자식 코루틴 실패
secondChild : 첫 번째 자식 코루틴 취소: true
supervisor : supervisor 취소
secondChild : 두 번째 자식 코루틴 취소 by supervisor cancellation
```

#### Supervision scope
- 범위 지정 동시성을 위해 `coroutineScope` 대신 `supervisorScope`를 사용할 수 있다.
- **취소를 한 방향으로만 전파하고, 실패한 경우에 모든 자식 코루틴을 취소한다.**
- **`coroutineScope` 와 동일하게 자식 코루틴이 완료될 때까지 기다린다.**

```kotlin
fun main() = runBlocking {
  try {
    supervisorScope {
      val child = launch {
        try {
          println("자식 코루틴 : 자식 코루틴 delay")
          delay(Long.MAX_VALUE)
        } finally {
          // supervisor 예외에 의해 자식 코루틴 취소
          println("자식 코루틴 : 자식 코루틴 취소")
        }
      }

      yield()
      println("supervisor : supervisor 스코프에서 예외 발생")
      throw AssertionError()
    }
  } catch (e: AssertionError) {
    println("catch AssertionError")
  }
}
```

- 결과

```text
자식 코루틴 : 자식 코루틴 delay
supervisor : supervisor 스코프에서 예외 발생
자식 코루틴 : 자식 코루틴 취소
catch AssertionError
```

- 자식 코루틴의 실패가 부모 코루틴으로 전파되지 않는다.
    - It means that coroutines launched directly inside the supervisorScope do use the CoroutineExceptionHandler that is installed in their scope in the same way as root coroutines do

```kotlin
fun main() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }

    supervisorScope {
        val child = launch(handler) {
            println("자식 코루틴 : throw AssertionError")
            throw AssertionError()
        }
        println("부모 scope : 완료 중")
    }
    // 자식 코루틴의 예외가 부모로 전파되지 않는다.
    println("부모 scope : 완료됨")
}
```

- 결과

```text
부모 scope : 완료 중
자식 코루틴 : throw AssertionError
CoroutineExceptionHandler got java.lang.AssertionError
부모 scope : 완료됨
```

## 코루틴 공식문서. [Shared mutable state and concurrency](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html)
- 코루틴은 멀티스레드 디스패처를 이용해서 동시 실행이 가능하다.
- 공유된 변견 가능한 상태에 동시에 접근할 때 동시성 문제가 발생한다.
- 멀티 스레드 문제를 해결하는 솔루션은 코루틴도 비슷하지만 코루틴에만 있는 몇 가지 특수한 솔루션이 있다.

### The problem
- 동시제어 없이 100개 코루틴이 동시에 conter++ 수행하기 때문에 100,000 얻을 수 없다.

```kotlin
var counter = 0

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}

suspend fun massiveRun(action: suspend () -> Unit) {
    val n = 100  // 100개 코루틴
    val k = 1000 // 한 개의 코루틴 안에서 1000번 수행
    val time = measureTimeMillis {
        coroutineScope { // 코루틴 스코프
            repeat(n) {
                launch {
                    repeat(k) { action() }
                }
            }
        }
    }
    println("Completed ${n * k} actions in $time ms")
}
```

- 결과

```text
Completed 100000 actions in 20 ms
Counter = 45863
```

- @Volatile 애노테이션이 동시성 문제를 해결해준다고 오해하지만 실제론 아니다.
- 코틀린의 @Volatile 애노테이션은 자바의 volatile 키워드와 동일하다.
  - volatile 키워드를 사용하면 멀티 스레드에서 CPU 캐시에 저장하는 게 아니라 메인 메모리에 값을 저장한다.
  - CPU 캐시에 저장하게 되면 서로 다른 스레드에서 읽기/쓰기를 했을 때 동기화가 되지 않는 문제가 발생할 수 있다.
  - CPU 캐시보다 메인 메모리가 비용이 더 큰 작업이다.
- @Volatile은 멀티 스레드에서 동시 쓰기에 대해 동시성을 보장해주진 않는다.

```kotlin
@Volatile 
var counter = 0

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}
```

- 결과

```text
Completed 100000 actions in 13 ms
Counter = 39893
```

### Thread-safe data structures
- 스레드 세이프한 자료구조를 이용해 원자성을 보장할 수 있다. (synchronized, linearizable, atomic)
- AtomicInteger 클래스 사용
- plain counters, 컬렉션, 큐, 표준 자료구조에서는 동작하지만 복잡한 작업으로 확장은 어렵다.

```kotlin
val atomicCounter = AtomicInteger()

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            atomicCounter.incrementAndGet()
        }
    }
    println("Counter = $atomicCounter")
}
```

- 결과

```text
Completed 100000 actions in 14 ms
Counter = 100000
```

### 스레드 한정 - fine-grained
```text
- coarse-grained : 결이 거친. 큰 단위로 묶어서
- fine-grained : 결이 부드러운. 작은 단위로 쪼개
```
- 스레드 한정 : 변경 가능한 state 접근을 싱글 스레드로 제한한다. (UI 변경은 특정 이벤트 디스패처나 스레드로 제한)
- 코루틴의 single thread context 사용해서 쉽게 적용할 수 있다.
- **작은 단위로 스레드를 한정하기 떄문에 느리게 동작한다.**
- 각각의 증가 연산은 멀티 스레드 Dispatcheers.Default 컨텍스트에서 단일 스레드 컨텍스트로 스위칭 된다.

```kotlin
@OptIn(ObsoleteCoroutinesApi::class)
fun main() {
    val counterContext = newSingleThreadContext("CounterContext")
    var counter = 0

    runBlocking {
        withContext(Dispatchers.Default) {  // Dispatchers.Default 디스패처로 한정 
            massiveRun {
                withContext(counterContext) {
                    // 싱글 스레드로 접근 제한
                    counter++
                }
            }
        }
    }
    println("Counter = $counter")
}
```

- 결과

````text
Completed 100000 actions in 499 ms
Counter = 100000
````

### 스레드 한정 - coarse-grained
- 실제 상황에서 스레드 한정은 더 큰 코드 블록 단위로 이루어진다. 예를들어 상태를 업데이트 하기 위해 큰 단위의 비지니스 로직들이 단일 스레드로 한정된다.
- 세밀한 한정과 비교해서 속도가 더 빠르다. (디스패처를 한정하지 않고 있음)

```kotlin
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0

fun main() = runBlocking {
    // confine everything to a single-threaded context
    withContext(counterContext) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}
```

- 결과

```text
Completed 100000 actions in 23 ms
Counter = 100000
```

### 상호배제
- 상호배제는 임계구역을 이용해 공유 상태의 모든 수정을 보호한다.
- **코루틴에서는 Mutex. 코루틴의 Mutex는 suspending function 이다.** (스레드를 블록하지 않는다.)
- lock, unlock 기능으로 임계구역(critical section) 제한한다.
- fine-grained 스레드 한정이기 때문에 성능면에서 손실이 있다.
- 주기적으로 상태 변경이 필요지만 스레드 한정을 적용하기 어려운 경우 활용.

```kotlin
fun main() {
    val mutex = Mutex()
    var counter = 0

    runBlocking {
        withContext(Dispatchers.Default) {
            massiveRun {
                // lock으로 변경 가능한 상태 보호
                // withLick : lock, unlock 수행
                mutex.withLock {
                    counter++
                }
            }
        }
        println("Counter = $counter")
    }
}
```

- 결과

```text
Completed 100000 actions in 154 ms
Counter = 100000
```

```kotlin
// withLock 확장함수
public suspend inline fun <T> Mutex.withLock(owner: Any? = null, action: () -> T): T {
    contract { 
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    lock(owner)
    try {
        return action()
    } finally {
        unlock(owner)
    }
}
```

### Actors
- 액터는 코루틴과 이 코루틴 내부로 `캡슐화된 상태 값`, 그리고 다른 코루틴과 통신할 수 있는 `채널`의 조합으로 구성된다.
- 간단한 액터는 함수로 작성될 수 있지만, 복잡한 경우 클래스로 정의하는 게 낫다.
- 액터 자체가 어떤 컨텍스트에서 실행되는지는 중요하지 않다. 액터는 코루틴이고 코루틴은 순차적으로 실행되기 때문에 특정변경 가능한 공유 상태 문제에 대한 솔루션으로 동작한다.
- 액터는 잠금보다 효율적이다. 일시정지 되면서 다른 코루틴이 계속 동작하고, 다른 컨텍스트로 전환이 없기 때문.

```kotlin
sealed class CounterMessage
object IncreaseCounter: CounterMessage()
class GetCounter(val response: CompletableDeferred<Int>) : CounterMessage()

@ObsoleteCoroutinesApi
fun CoroutineScope.counterActor() = actor<CounterMessage> {
    var counter = 0
    for (message in channel) {
        when (message) {
            is IncreaseCounter -> counter++
            is GetCounter -> message.response.complete(counter)
        }
    }
}

@ObsoleteCoroutinesApi
fun main() = runBlocking<Unit> {
    val counter = counterActor()  // 액터 생성
    withContext(Dispatchers.Default) {
        massiveRun {  // 람다식
            counter.send(IncreaseCounter)
        }
    }
    val response = CompletableDeferred<Int>()
    counter.send(GetCounter(response))
    println("Counter = ${response.await()}")
    counter.close()
}
```

## 과제
- 순착순 이벤트 : 100개로 제한된 티케팅을 한다. 한 번에 하나씩만 티켓을 구매할 수 있다. 
```text
ticketingLimit : 100
- 티케팅은 코루틴을 이용해 동시 처리
- 100개가 모두 티케팅 되면 구매하려고 할 때 Exception 전파되어서 종료된다.
```
