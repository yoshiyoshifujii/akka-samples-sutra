package sample.cqrs

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster

object Guardian {

  def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    implicit val system: ActorSystem[Nothing] = context.system
    val settings = EventProcessorSettings(system)
    val httpPort = context.system.settings.config.getInt("shopping.http.port")

    ShoppingCart.init(system, settings)

    if (Cluster(system).selfMember.hasRole("read-model")) {
      EventProcessor.init(
        system,
        settings,
        tag => new ShoppingCartEventProcessorStream(system, system.executionContext, settings.id, tag)
      )
    }

    val routes = new ShoppingCartRoutes()
    new ShoppingCartServer(routes.shopping, httpPort, context.system).start()

    Behaviors.empty
  }

}
