package sample.cluster.stats

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.cluster.typed.{
  Cluster,
  ClusterSingleton,
  ClusterSingletonSettings,
  SingletonActor
}
import com.typesafe.config.ConfigFactory

object AppOneMaster extends App {

  val WorkerServiceKey = ServiceKey[StatsWorker.Process]("Worker")

  val RoleCompute = "compute"

  val RoleClient = "client"

  object RootBehavior {

    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      val singletonSettings = ClusterSingletonSettings(ctx.system)
        .withRole(RoleCompute)
      val serviceSingleton = SingletonActor(
        Behaviors.setup[StatsService.Command] { context =>
          val workersRouter =
            context.spawn(Routers.group(WorkerServiceKey), "WorkersRouter")
          StatsService(workersRouter)
        },
        "StatsService"
      ).withStopMessage(StatsService.Stop)
        .withSettings(singletonSettings)
      val serviceProxy = ClusterSingleton(ctx.system).init(serviceSingleton)

      if (cluster.selfMember.hasRole(RoleCompute)) {
        val numberOfWorkers =
          ctx.system.settings.config.getInt("stats-service.workers-per-node")
        ctx.log.info("Starting {} workers", numberOfWorkers)
        (0 to numberOfWorkers).foreach { n =>
          val worker = ctx.spawn(StatsWorker(), s"StatsWorker$n")
          ctx.system.receptionist ! Receptionist.Register(WorkerServiceKey,
                                                          worker)
        }
      }

      if (cluster.selfMember.hasRole(RoleClient)) {
        ctx.spawn(StatsClient(serviceProxy), "Client")
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
    startup(RoleCompute, 25251)
    startup(RoleCompute, 25252)
    startup(RoleCompute, 0)
    startup(RoleClient, 0)
  } else {
    require(args.size == 2, "Usage: role port")
    startup(args(0), args(1).toInt)
  }
}
