package com.github.yoshiyoshifujii.akka.sample.router

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.DispatcherSelector
import akka.actor.typed.receptionist.Receptionist
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec

class WorkerSpec extends AnyFreeSpec with BeforeAndAfterAll {

  private val testKit: ActorTestKit = ActorTestKit()

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  "Worker" - {

    "worker success" in {

      val ref = testKit.spawn(Worker(), "worker")

      (0 to 10).foreach { n =>
        ref ! Worker.DoLog(s"worker msg $n")
      }
      succeed

    }

    "router success" in {

      val ref = testKit.spawn(Worker.pool, "worker-pool")

      (0 to 10).foreach { n =>
        ref ! Worker.DoLog(s"router msg $n")
      }
      succeed

    }

    "blocking router success" in {

      val ref = testKit.spawn(Worker.blockingPool, "worker-blocking-pool", DispatcherSelector.sameAsParent())

      (0 to 10).foreach { n =>
        ref ! Worker.DoLog(s"blocking msg $n")
      }
      succeed

    }

    "group router success" in {

      val worker = testKit.spawn(Worker(), "worker2")
      testKit.system.receptionist ! Receptionist.Register(Worker.serviceKey, worker)

      val ref = testKit.spawn(Worker.group, "worker-group")

      (0 to 10).foreach { n =>
        ref ! Worker.DoLog(s"group router msg $n")
      }
      succeed
    }
  }

}
