package com.github.yoshiyoshifujii.akka.samples.cluster.sharding.persistenceExample

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.persistence.typed.PersistenceId
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class HelloWorldService(system: ActorSystem[_]) {
  private val clusterSharding                            = ClusterSharding(system)
  private val TypeKey: EntityTypeKey[HelloWorld.Command] = EntityTypeKey[HelloWorld.Command](HelloWorld.name)

  clusterSharding.init(
    Entity[HelloWorld.Command](TypeKey) { entityContext =>
      HelloWorld(
        PersistenceId.of(entityContext.entityTypeKey.name, entityContext.entityId, "-")
      )
    }
  )

  import system.executionContext
  private implicit val askTimeout: Timeout = Timeout(5.seconds)

  def greet(worldId: String, whom: String): Future[Int] = {
    val entityRef = clusterSharding.entityRefFor(TypeKey, worldId)
    val greeting  = entityRef ? HelloWorld.CommandGreet(whom)
    greeting.map(_.numberOfPeople)
  }
}
