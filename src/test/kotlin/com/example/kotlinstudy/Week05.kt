package com.example.kotlinstudy

import com.example.kotlinstudy.coroutines.Car
import com.example.kotlinstudy.coroutines.CarDoor
import com.example.kotlinstudy.coroutines.CarFrame
import com.example.kotlinstudy.coroutines.CarTire
import com.example.kotlinstudy.coroutines.Employee
import com.example.kotlinstudy.coroutines.EmployeeRepository
import com.example.kotlinstudy.coroutines.log
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class Week05 {
    val employeeRepository: EmployeeRepository = EmployeeRepository()
    val random = Random(1)

    @OptIn(ExperimentalTime::class)
    @Test
    fun `5대의 자동차를 가장 빠르게 만들어보자`() {
        val time = measureTime {
            var count = 0
            for (i in 0..4) {
                log("자동차 생산 시작")

                // FIXME: 코루틴을 이용해 생산 프로세스를 최적화 하기
                val employee: Employee = getEmployee()
                val employee2: Employee = getEmployee()
                val carFrame: CarFrame = CarFrame()
                val carDoor: CarDoor = CarDoor(carFrame = carFrame)
                carDoor.assemble(employee, carFrame)

                val carTire: CarTire = CarTire(carFrame = carFrame)
                carTire.assemble(employee, carFrame)

                val car: Car = Car(carFrame = carFrame, carDoor = carDoor, carTire = carTire)
                car.assemble(employee1 = employee, employee2 = employee2, carFrame, carDoor, carTire)

                log("자동차 생산 완료")
                count++
            }

            count shouldBe 5
        }
        time.shouldBeLessThan(Duration.Companion.seconds(50))

    }

    fun getEmployee(): Employee = employeeRepository.findEmployee(getEmployeeId())
    fun getEmployeeId() = random.nextLong(1, 5)

}
