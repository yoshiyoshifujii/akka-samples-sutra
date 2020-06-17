package com.github.yoshiyoshifujii.akka.sample.router

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{Behaviors, GroupRouter, PoolRouter, Routers}
import akka.actor.typed.{Behavior, DispatcherSelector, SupervisorStrategy}

object Worker {

  sealed trait Command
  case class DoLog(test: String) extends Command

  val serviceKey: ServiceKey[Command] = ServiceKey[Command]("log-worker")

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Starting worker")

      Behaviors.receiveMessage {
        case DoLog(text) =>
          context.log.info("Got message {}", text)
          Behaviors.same
      }

    }

  lazy val pool: PoolRouter[Command] = Routers.pool(poolSize = 4)(
    Behaviors.supervise(Worker()).onFailure[Exception](SupervisorStrategy.restart)
  )

  lazy val blockingPool: PoolRouter[Command] = pool.withRouteeProps(routeeProps = DispatcherSelector.blocking())

  lazy val group: GroupRouter[Command] = Routers.group(serviceKey)

  lazy val roundRobinRoutingPool: PoolRouter[Command] = pool.withPoolSize(2).withRoundRobinRouting().withRouteeProps()

}
