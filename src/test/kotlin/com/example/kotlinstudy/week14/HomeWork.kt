package com.example.kotlinstudy.week14

import com.example.kotlinstudy.coroutines.log
import com.example.kotlinstudy.temp_woojin.sleep
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HomeWork {


    @Test
    fun `신분증 인증을 할 때 가장 먼저 인증된 값만 확인 후 테스트 종료한다`() {
        repeat(5) {
            val 금결원 = action("금결원", random.nextLong(5), random.nextBoolean())
            val 행안부 = action("행안부", random.nextLong(30),  random.nextBoolean())
            val 인증기관리스트 = mutableListOf(금결원, 행안부)

            log("신분증 인증 시작")
            runBlocking {
                for (i in 인증기관리스트.indices) {
                    val auth = select<Auth> {
                        인증기관리스트.forEachIndexed { index, deferred ->
                            deferred.onAwait { result ->
                                인증기관리스트.removeAt(index)
                                log("name: ${result.name}, result : ${result.result}")
                                result
                            }
                        }
                    }
                    log("find auth : $auth")
                    if (auth.result) {
                        log("${인증기관리스트.count { it.isActive }} coroutines are still active")
                        break
                    }
                }
            }
            log("신분증 인증 종료")
        }

    }

    val random = Random(5)

    private fun action(name: String, time: Long, result: Boolean) = GlobalScope.async {
        log("name: $name, sleep: $time, result: $result")
        sleep(time)
        Auth(name, result)
    }
}

data class Auth(
    val name: String,
    val result: Boolean
)
