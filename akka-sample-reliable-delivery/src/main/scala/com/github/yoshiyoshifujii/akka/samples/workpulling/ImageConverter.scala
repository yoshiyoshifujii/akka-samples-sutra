package com.github.yoshiyoshifujii.akka.samples.workpulling

import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.actor.typed.Behavior
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors

object ImageConverter {

  final case class ConversionJob(resultId: UUID, fromFormat: String, toFormat: String, image: Array[Byte])

  sealed trait Command
  private case class WrappedDelivery(d: ConsumerController.Delivery[ConversionJob]) extends Command

  val serviceKey: ServiceKey[ConsumerController.Command[ConversionJob]] =
    ServiceKey[ConsumerController.Command[ConversionJob]]("ImageConverter")

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val deliveryAdapter    = context.messageAdapter[ConsumerController.Delivery[ConversionJob]](WrappedDelivery)
      val consumerController = context.spawn(ConsumerController(serviceKey), "consumerController")
      consumerController ! ConsumerController.Start(deliveryAdapter)

      Behaviors.receiveMessage {
        case WrappedDelivery(delivery) =>
          val image      = delivery.message.image
          val fromFormat = delivery.message.fromFormat
          val toFormat   = delivery.message.toFormat
          // convert image...
          // store result with resultId key for later retrieval
          context.log.info(
            "convert image and stored. {}, {}, {}",
            new String(image, StandardCharsets.UTF_8),
            fromFormat,
            toFormat
          )

          // and when completed confirm
          delivery.confirmTo ! ConsumerController.Confirmed

          Behaviors.same
      }
    }

}
