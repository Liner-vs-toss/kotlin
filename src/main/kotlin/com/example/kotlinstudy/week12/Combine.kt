package com.example.kotlinstudy.week12
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


fun main() = runBlocking<Unit> {
    val nums = (1..3).asFlow().onEach { delay(300) } // numbers 1..3 every 300 ms
    val strs = flowOf("one", "two", "three").onEach { delay(400) } // strings every 400 ms
    val startTime = System.currentTimeMillis() // remember the start time
            //nums.zip(strs) { a, b -> "$a -> $b" } // compose a single string with "zip"
            nums.combine(strs) { a, b -> "$a -> $b" } // compose a single string with "zip"
            .collect { value -> // collect and print
                println("$value at ${System.currentTimeMillis() - startTime} ms from start")
            }
}