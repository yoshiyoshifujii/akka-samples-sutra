package sample.cluster.transformation

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object Main extends App {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      if (cluster.selfMember.hasRole("backend")) {
        val workersPerNode = ctx.system.settings.config.getInt("transformation.workers-per-node")
        (1 to workersPerNode).foreach { n =>
          ctx.spawn(Worker(), s"Worker$n")
        }
      }

      if (cluster.selfMember.hasRole("frontend")) {
        ctx.spawn(Frontend(), "Frontend")
      }
      Behaviors.empty
    }
  }

  if (args.isEmpty) {
    startup("backend", 25251)
    startup("backend", 25252)
    startup("frontend", 0)
    startup("frontend", 0)
    startup("frontend", 0)
  } else {
    require(args.length == 2, "Usage: role port")
    startup(args(0), args(1).toInt)
  }

  def startup(role: String, port: Int): Unit = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port=$port
         |akka.cluster.roles = [$role]""".stripMargin)
        .withFallback(ConfigFactory.load("transformation"))

    ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }
}
