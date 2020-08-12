package com.github.yoshiyoshifujii.akka.samples.pointtopoint

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.delivery.{ ConsumerController, ProducerController }
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpecLike

class FibonacciSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.parseString("""
    |
    |""".stripMargin))
    with AnyFreeSpecLike {

  "Fibonacci" - {

    "success" in {

      val consumerController = testKit.spawn(ConsumerController[FibonacciConsumer.Command](), "consumerController")
      testKit.spawn(FibonacciConsumer(consumerController), "consumer")

      val producerId = s"fibonacci-${UUID.randomUUID()}"
      val producerController = testKit.spawn(
        ProducerController[FibonacciConsumer.Command](producerId, durableQueueBehavior = None),
        "producerController"
      )
      testKit.spawn(FibonacciProducer(producerController), "producer")

      consumerController ! ConsumerController.RegisterToProducerController(producerController)
    }

  }

}
