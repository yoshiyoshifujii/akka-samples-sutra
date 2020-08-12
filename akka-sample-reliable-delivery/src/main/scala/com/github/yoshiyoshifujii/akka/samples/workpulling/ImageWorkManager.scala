package com.github.yoshiyoshifujii.akka.samples.workpulling

import java.util.UUID

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.delivery.WorkPullingProducerController
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }

object ImageWorkManager {

  sealed trait Command
  final case class Convert(fromFormat: String, toFormat: String, image: Array[Byte]) extends Command

  private case class WrappedRequestNext(r: WorkPullingProducerController.RequestNext[ImageConverter.ConversionJob])
      extends Command

  final case class GetResult(resultId: UUID, replyTo: ActorRef[Option[Array[Byte]]]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val requestNextAdapter = context
        .messageAdapter[WorkPullingProducerController.RequestNext[ImageConverter.ConversionJob]](WrappedRequestNext)
      val producerController = context.spawn(
        WorkPullingProducerController.apply(
          producerId = "workManager",
          workerServiceKey = ImageConverter.serviceKey,
          durableQueueBehavior = None
        ),
        "producerController"
      )
      producerController ! WorkPullingProducerController.Start(requestNextAdapter)

      Behaviors.withStash(100) { stashBuffer =>
        new ImageWorkManager(context, stashBuffer).waitForNext()
      }
    }

}

class ImageWorkManager(
    context: ActorContext[ImageWorkManager.Command],
    stashBuffer: StashBuffer[ImageWorkManager.Command]
) {
  import ImageWorkManager._

  private def active(next: WorkPullingProducerController.RequestNext[ImageConverter.ConversionJob]): Behavior[Command] =
    Behaviors.receiveMessage {
      case Convert(from, to, image) =>
        val resultId = UUID.randomUUID()
        next.sendNextTo ! ImageConverter.ConversionJob(resultId, from, to, image)
        waitForNext()

      case GetResult(resultId, replyTo) =>
        context.log.info("retrieve the stored result and reply. {}, {}", resultId, replyTo)
        Behaviors.same

      case _: WrappedRequestNext =>
        throw new IllegalStateException(s"Unexpected RequestNext [${this.getClass.getName}]")
    }

  private def waitForNext(): Behavior[ImageWorkManager.Command] =
    Behaviors.receiveMessage {
      case WrappedRequestNext(next) =>
        stashBuffer.unstashAll(active(next))

      case c: Convert =>
        if (stashBuffer.isFull) {
          context.log.warn("Too many Convert requests.")
          Behaviors.same
        } else {
          stashBuffer.stash(c)
          Behaviors.same
        }

      case GetResult(resultId, replyTo) =>
        context.log.info("retrieve the stored result and reply. {}, {}", resultId, replyTo)
        Behaviors.same
    }

}
