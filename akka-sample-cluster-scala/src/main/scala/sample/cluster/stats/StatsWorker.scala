package sample.cluster.stats

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import sample.cluster.CborSerializable

import scala.concurrent.duration._

object StatsWorker {

  final case class Processed(word: String, length: Int) extends CborSerializable

  sealed trait Command
  final case class Process(word: String, replyTo: ActorRef[Processed]) extends Command with CborSerializable
  private case object EvictCache extends Command

  def withCache(ctx: ActorContext[Command], cache: Map[String, Int]): Behavior[Command] =
    Behaviors.receiveMessage {
      case Process(word, replyTo) =>
        ctx.log.info("worker processing request")
        cache.get(word) match {
          case Some(length) =>
            replyTo ! Processed(word, length)
            Behaviors.same
          case None =>
            val length = word.length
            val updatedCache = cache + (word -> length)
            replyTo ! Processed(word, length)
            withCache(ctx, updatedCache)
        }
      case EvictCache =>
        withCache(ctx, Map.empty)
  }

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      ctx.log.info("Worker starting up")
      timers.startTimerWithFixedDelay(EvictCache, 30.seconds)

      withCache(ctx, Map.empty)
    }
  }

}
