package sample.cluster.simple

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

object Main extends App {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      context.spawn(ClusterListener(), "ClusterListener")
      Behaviors.empty
    }
  }

  def startup(port: Int): Unit = {
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      """).withFallback(ConfigFactory.load())

    ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }

  val ports = if (args.isEmpty) Seq(25251, 25252, 0) else args.toSeq.map(_.toInt)
  ports.foreach(startup)

}
