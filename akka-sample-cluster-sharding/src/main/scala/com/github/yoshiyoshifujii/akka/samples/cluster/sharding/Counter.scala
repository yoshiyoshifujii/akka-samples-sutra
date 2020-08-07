package com.github.yoshiyoshifujii.akka.samples.cluster.sharding

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef, EntityTypeKey }

object Counter {
  val name: String = "Counter"

  sealed trait Command {
    def id: String
  }
  final case class Increment(id: String)                        extends Command
  final case class GetValue(id: String, replyTo: ActorRef[Int]) extends Command

  def apply(entityId: String): Behavior[Command] = {
    def updated(value: Int): Behavior[Command] =
      Behaviors.receiveMessage {
        case Increment(id) if entityId == id =>
          updated(value + 1)
        case GetValue(id, replyTo) if entityId == id =>
          replyTo ! value
          Behaviors.same
      }

    updated(0)
  }

}

object ShardedCounter {

  private val TypeKey: EntityTypeKey[Counter.Command] = EntityTypeKey[Counter.Command](Counter.name)

  def init(clusterSharding: ClusterSharding): ActorRef[ShardingEnvelope[Counter.Command]] =
    clusterSharding.init(Entity(TypeKey)(createBehavior = entityContext => Counter(entityContext.entityId)))

  def ofProxy(clusterSharding: ClusterSharding): Behavior[Counter.Command] =
    Behaviors.receiveMessage { msg =>
      val entityRef: EntityRef[Counter.Command] = clusterSharding.entityRefFor(TypeKey, msg.id)
      entityRef ! msg
      Behaviors.same
    }
}
