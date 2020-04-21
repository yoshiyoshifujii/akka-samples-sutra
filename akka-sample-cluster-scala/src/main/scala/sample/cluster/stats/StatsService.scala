package sample.cluster.stats

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import sample.cluster.CborSerializable

import scala.concurrent.duration._

object StatsService {

  sealed trait Command extends CborSerializable
  final case class ProcessText(text: String, replyTo: ActorRef[Response]) extends Command {
    require(text.nonEmpty)
  }
  case object Stop extends Command

  sealed trait Response extends CborSerializable
  final case class JobResult(meanWordLength: Double) extends Response
  final case class JobFailed(reason: String) extends Response

  def apply(workers: ActorRef[StatsWorker.Process]): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.watch(workers)

    Behaviors.receiveMessage {
      case ProcessText(text, replyTo) =>
        ctx.log.info("Delegating request")
        val words = text.split(' ').toIndexedSeq
        ctx.spawnAnonymous(StatsAggregator(words, workers, replyTo))
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }

}

object StatsAggregator {

  sealed trait Event
  private case object Timeout extends Event
  private case class CalculationComplete(length: Int) extends Event

  def waiting(replyTo: ActorRef[StatsService.Response], expectedResponse: Int, results: List[Int]):Behavior[StatsAggregator.Event] =
    Behaviors.receiveMessage {
      case CalculationComplete(length) =>
        val newResults  = results :+ length
        if (newResults.size == expectedResponse) {
          val meanWordLength = newResults.sum.toDouble / newResults.size
          replyTo ! StatsService.JobResult(meanWordLength)
          Behaviors.stopped
        } else {
          waiting(replyTo, expectedResponse, newResults)
        }
      case Timeout =>
        replyTo ! StatsService.JobFailed("Service unavailable, try again later")
        Behaviors.stopped
    }

  def apply(words: Seq[String], workers: ActorRef[StatsWorker.Process], replyTo: ActorRef[StatsService.Response]): Behavior[Event] =
    Behaviors.setup { ctx =>
      ctx.setReceiveTimeout(3.seconds, Timeout)
      val responseAdapter = ctx.messageAdapter[StatsWorker.Processed](processed =>
        CalculationComplete(processed.length)
      )

      words.foreach { word =>
        workers ! StatsWorker.Process(word, responseAdapter)
      }
      waiting(replyTo, words.size, Nil)
    }
}

