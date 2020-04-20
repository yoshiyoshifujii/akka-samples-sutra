package sample.cluster.transformation

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import sample.cluster.CborSerializable

object Worker {

  val WorkerServiceKey: ServiceKey[TransformText] = ServiceKey[Worker.TransformText]("Worker")

  final case class TextTransformed(text: String) extends CborSerializable

  sealed trait Command
  final case class TransformText(text: String, replyTo: ActorRef[TextTransformed]) extends Command with CborSerializable

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Registering myself with receptionist")
    ctx.system.receptionist ! Receptionist.Register(WorkerServiceKey, ctx.self)

    Behaviors.receiveMessage {
      case TransformText(text, replyTo) =>
        replyTo ! TextTransformed(text.toUpperCase)
        Behaviors.same
    }
  }

}
