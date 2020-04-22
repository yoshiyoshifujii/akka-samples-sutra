package sample.cluster.stats

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object Main extends App {

  val StatsServiceKey = ServiceKey[StatsService.ProcessText]("StatsService")

  private object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      if (cluster.selfMember.hasRole("compute")) {
        val numberOfWorkers = ctx.system.settings.config.getInt("stats-service.workers-per-node")
        val workers = ctx.spawn(Routers.pool(numberOfWorkers)(StatsWorker()), "WorkerRouter")
        val service = ctx.spawn(StatsService(workers), "StatsService")

        ctx.system.receptionist ! Receptionist.Register(StatsServiceKey, service)
      }

      if (cluster.selfMember.hasRole("client")) {
        val serviceRouter = ctx.spawn(Routers.group(StatsServiceKey), "ServiceRouter")
        ctx.spawn(StatsClient(serviceRouter), "Client")
      }
      Behaviors.empty
    }
  }

  private def startup(role: String, port: Int): Unit = {
    val config = ConfigFactory.parseString(
      s"""
        |akka.remote.artery.canonical.port=$port
        |akka.cluster.roles = [$role]""".stripMargin)
      .withFallback(ConfigFactory.load("stats"))
    ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }

  if (args.isEmpty) {
    startup("compute", 25251)
    startup("compute", 25252)
    startup("compute", 0)
    startup("client", 0)
  } else {
    require(args.length == 2, "Usage: role port")
    startup(args(0), args(1).toInt)
  }

}
