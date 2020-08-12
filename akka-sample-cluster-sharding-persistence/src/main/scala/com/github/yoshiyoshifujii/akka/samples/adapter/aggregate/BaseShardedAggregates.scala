package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityContext, EntityTypeKey }
import com.github.yoshiyoshifujii.akka.samples.domain.`type`.{ Id => DomainId }

import scala.concurrent.duration._
import scala.reflect.ClassTag

abstract class BaseShardedAggregates[
    Id <: DomainId,
    Command <: BaseCommand[Id]: ClassTag,
    Idle <: BaseIdle[Id] with Command: ClassTag,
    Stop <: BaseStop[Id] with Command: ClassTag
] {

  def name: String
  protected def typeKeyName: String
  protected def actorName: String
  protected def idle: Idle
  protected def stop: Stop

  lazy val TypeKey: EntityTypeKey[Command] = EntityTypeKey(typeKeyName)

  private def entityBehavior(
      childBehavior: Behavior[Command],
      receiveTimeout: FiniteDuration
  ): EntityContext[Command] => Behavior[Command] = { entityContext =>
    Behaviors.setup[Command] { ctx =>
      val childRef = ctx.spawn(childBehavior, actorName)
      ctx.setReceiveTimeout(receiveTimeout, idle)
      Behaviors.receiveMessage {
        case _: Idle =>
          entityContext.shard ! ClusterSharding.Passivate(ctx.self)
          Behaviors.same
        case _: Stop =>
          ctx.log.debug("> Changed state: Stop [{}]", ctx.self.path.address)
          Behaviors.stopped
        case msg =>
          childRef ! msg
          Behaviors.same
      }
    }
  }

  def initClusterSharding(
      clusterSharding: ClusterSharding,
      childBehavior: Behavior[Command],
      receiveTimeout: FiniteDuration
  ): ActorRef[ShardingEnvelope[Command]] =
    clusterSharding.init(
      Entity(TypeKey)(entityBehavior(childBehavior, receiveTimeout)).withStopMessage(stop)
    )

  def ofProxy(clusterSharding: ClusterSharding): Behavior[Command] =
    Behaviors.receiveMessage[Command] { msg =>
      val entityRef = clusterSharding.entityRefFor[Command](TypeKey, msg.idAsString.reverse)
      entityRef ! msg
      Behaviors.same
    }

}
