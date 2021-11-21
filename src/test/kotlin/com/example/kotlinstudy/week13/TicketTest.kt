package com.example.kotlinstudy.week13

import com.example.kotlinstudy.coroutines.log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.runBlocking
import org.jboss.logging.Logger
import org.junit.jupiter.api.Test

class TicketTest {
    val logger = Logger.getLogger(this::class.java)

    @Test
    fun ticket() {
        runBlocking(Dispatchers.IO) {
            val ticketActor = actor<TicketMessage> {
                var ticketCount = 100
                for (msg in channel) {
                    when (msg) {
                        TicketMessage.BUY -> {
                            if (ticketCount <= 0) {
                                log("티켓이 다 팔렸습니다.")
                                return@actor
//                                throw TicketException("티켓이 다팔렸습니다.")
                            }
                            log("현재 남은 티켓 count : ${ticketCount--}")
                        }
                    }
                }
            }

            val users = mutableListOf<Deferred<Any>>()
            for (i in 1..1000) {
                val user = async {
                    try {
                        ticketActor.send(TicketMessage.BUY)
                    } catch (e: Exception) {
//                        logger.info("{}",e)
                    }
                }
                users.add(user)
            }
            try {
                awaitAll(*users.toTypedArray())
            } catch (e: Exception) {
                logger.info("ticket sold out", e)
            }

            ticketActor.close()
        }
    }

    enum class TicketMessage {
        OPEN, BUY, CLOSED
    }

    class TicketException(override val message: String) : RuntimeException(message)
}
