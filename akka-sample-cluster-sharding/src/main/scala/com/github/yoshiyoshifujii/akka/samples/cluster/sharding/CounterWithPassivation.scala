package com.github.yoshiyoshifujii.akka.samples.cluster.sharding

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef, EntityTypeKey }

import scala.concurrent.duration.DurationInt

object Counter2 {
  val name: String = "Counter"

  sealed trait Command {
    def id: String
  }
  final case class Increment(id: String)                        extends Command
  final case class GetValue(id: String, replyTo: ActorRef[Int]) extends Command

  trait UnsupportedCommand {
    self: Command =>
    override def id: String = throw new UnsupportedOperationException(s"${this.getClass.getName}")
  }

  private case object Idle  extends Command with UnsupportedCommand
  case object GoodByCounter extends Command with UnsupportedCommand

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], entityId: String): Behavior[Command] =
    Behaviors.setup { context =>
      def updated(value: Int): Behavior[Command] =
        Behaviors.receiveMessage {
          case Increment(id) if entityId == id =>
            updated(value + 1)
          case GetValue(id, replyTo) if entityId == id =>
            replyTo ! value
            Behaviors.same
          case Idle =>
            // after receive timeout
            shard ! ClusterSharding.Passivate(context.self)
            Behaviors.same
          case GoodByCounter =>
            Behaviors.stopped
        }

      context.setReceiveTimeout(30.seconds, Idle)
      updated(0)
    }

}

object ShardedCounter2 {

  private val TypeKey: EntityTypeKey[Counter2.Command] = EntityTypeKey[Counter2.Command](Counter2.name)

  def init(clusterSharding: ClusterSharding): ActorRef[ShardingEnvelope[Counter2.Command]] =
    clusterSharding.init(
      Entity(TypeKey)(createBehavior =
        entityContext =>
          Counter2(
            entityContext.shard,
            entityContext.entityId
          )
      ).withStopMessage(Counter2.GoodByCounter)
    )

  def ofProxy(clusterSharding: ClusterSharding): Behavior[Counter2.Command] =
    Behaviors.receiveMessage { msg =>
      val entityRef: EntityRef[Counter2.Command] = clusterSharding.entityRefFor(TypeKey, msg.id)
      entityRef ! msg
      Behaviors.same
    }
}
