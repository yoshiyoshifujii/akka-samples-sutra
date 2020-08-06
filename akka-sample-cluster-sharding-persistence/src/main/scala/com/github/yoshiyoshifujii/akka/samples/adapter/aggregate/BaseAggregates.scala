package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import com.github.yoshiyoshifujii.akka.samples.domain.`type`.{ Id => DomainId }

trait BaseCommand[Id <: DomainId] {
  def id: Id
  def idAsString: String = id.asString
}

trait UnsupportedId[Id <: DomainId] {
  self: BaseCommand[Id] =>
  override def id: Id             = throw new UnsupportedOperationException(s"${this.getClass.getName}")
  override def idAsString: String = throw new UnsupportedOperationException(s"${this.getClass.getName}")
}

trait BaseIdle[Id <: DomainId] extends BaseCommand[Id] with UnsupportedId[Id]
trait BaseStop[Id <: DomainId] extends BaseCommand[Id] with UnsupportedId[Id]

trait BaseAggregates[Id <: DomainId, Command <: BaseCommand[Id]] {

  def name: String

  def apply(nameF: Id => String)(childBehaviorF: Id => Behavior[Command]): Behavior[Command] =
    Behaviors.setup { ctx =>
      def getOrCreateRef(id: Id): ActorRef[Command] = {
        ctx.child(nameF(id)) match {
          case None =>
            ctx.log.debug("spawn: child = {}", id)
            ctx.spawn(
              childBehaviorF(id),
              nameF(id)
            )
          case Some(ref) =>
            ref.asInstanceOf[ActorRef[Command]]
        }
      }

      Behaviors.receiveMessage[Command] { msg =>
        getOrCreateRef(msg.id) ! msg
        Behaviors.same
      }
    }

}
