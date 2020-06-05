package com.github.yoshiyoshifujii.samples.adaptedResponse

import java.net.URI

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {

  object Backend {
    sealed trait Request
    final case class StartTranslationJob(taskId: Int, site: URI, replyTo: ActorRef[Response]) extends Request

    sealed trait Response
    final case class JobStarted(taskId: Int) extends Response
    final case class JobProgress(taskId: Int, progress: Double) extends Response
    final case class JobCompleted(taskId: Int, result: URI) extends Response

    def apply(): Behavior[Request] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case StartTranslationJob(taskId, site, replyTo) =>
            context.log.info("StartTranslationJob {}: {}", taskId, site)
            replyTo ! JobStarted(taskId)
            replyTo ! JobProgress(taskId, 1d)
            replyTo ! JobCompleted(taskId, site)
            Behaviors.same
        }
      }
  }

  object Frontend {
    sealed trait Command
    final case class Translate(site: URI, replyTo: ActorRef[URI]) extends Command
    private final case class WrappedBackendResponse(response: Backend.Response) extends Command

    def apply(backend: ActorRef[Backend.Request]): Behavior[Command] =
      Behaviors.setup { context =>
        val backendResponseMapper = context.messageAdapter[Backend.Response](rsp => WrappedBackendResponse(rsp))

        def active(inProgress: Map[Int, ActorRef[URI]], count: Int): Behavior[Command] = {
          Behaviors.receiveMessage {
            case Translate(site, replyTo) =>
              val taskId = count + 1
              backend ! Backend.StartTranslationJob(taskId, site, backendResponseMapper)
              active(inProgress.updated(taskId, replyTo), taskId)
            case WrappedBackendResponse(Backend.JobStarted(taskId)) =>
              context.log.info("Started {}", taskId)
              Behaviors.same
            case WrappedBackendResponse(Backend.JobProgress(taskId, progress)) =>
              context.log.info("Progress {}: {}", taskId, progress)
              Behaviors.same
            case WrappedBackendResponse(Backend.JobCompleted(taskId, result)) =>
              context.log.info("Completed {}: {}", taskId, result)
              inProgress(taskId) ! result
              active(inProgress - taskId, count)
          }
        }
        active(inProgress = Map.empty, count = 0)
      }
  }

  object Guardian {

    def apply(): Behavior[AnyRef] =
      Behaviors.setup[AnyRef] { context =>
        import akka.actor.typed.scaladsl.AskPattern._
        implicit val timeout = Timeout(5.seconds)
        implicit val scheduler = context.system.scheduler

        val backend = context.spawn(Backend(), "backend")
        val frontend = context.spawn(Frontend(backend), "frontend")

        val future = frontend.ask[URI](reply => Frontend.Translate(new URI("hoge"), reply))
        val result = Await.result(future, Duration.Inf)

        context.log.info("{}", result)

        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "adapted-response")

  sys.addShutdownHook {
    system.terminate()
  }

}
