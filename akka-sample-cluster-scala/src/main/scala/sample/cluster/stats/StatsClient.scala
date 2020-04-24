package sample.cluster.stats

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._

object StatsClient {

  sealed trait Event
  private case object Tick extends Event
  private case class ServiceResponse(result: StatsService.Response)
      extends Event

  def apply(service: ActorRef[StatsService.ProcessText]): Behavior[Event] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Tick, 2.seconds)
        val responseAdapter = ctx.messageAdapter(ServiceResponse)

        Behaviors.receiveMessage {
          case Tick =>
            ctx.log.info("Sending process request")
            service ! StatsService.ProcessText("this is the text that will be analyzed", responseAdapter)
            Behaviors.same
          case ServiceResponse(result) =>
            ctx.log.info("Service result: {}", result)
            Behaviors.same
        }
      }
    }

}
