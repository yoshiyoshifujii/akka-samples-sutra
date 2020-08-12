package com.github.yoshiyoshifujii.akka.samples.pointtopoint

import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }

object FibonacciConsumer {

  sealed trait Command
  final case class FibonacciNumber(n: Long, value: BigInt)                    extends Command
  private case class WrappedDelivery(d: ConsumerController.Delivery[Command]) extends Command

  def apply(consumerController: ActorRef[ConsumerController.Command[Command]]): Behavior[Command] =
    Behaviors.setup { context =>
      val deliveryAdapter = context.messageAdapter[ConsumerController.Delivery[Command]](WrappedDelivery)
      consumerController ! ConsumerController.Start(deliveryAdapter)

      Behaviors.receiveMessagePartial {
        case WrappedDelivery(ConsumerController.Delivery(FibonacciNumber(n, value), confirmTo)) =>
          context.log.info("Processed fibonacci {}: {}", n, value)
          confirmTo ! ConsumerController.Confirmed
          Behaviors.same
      }
    }

}
