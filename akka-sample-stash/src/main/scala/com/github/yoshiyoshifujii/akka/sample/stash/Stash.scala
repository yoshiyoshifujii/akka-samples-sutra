package com.github.yoshiyoshifujii.akka.sample.stash

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Stash {

  trait DB {
    def save(id: String, value: String): Future[Done]
    def load(id: String): Future[String]
  }

  object DataAccess {
    sealed trait Command
    final case class Save(value: String, replyTo: ActorRef[Done]) extends Command
    final case class Get(replyTo: ActorRef[String]) extends Command
    private final case class InitialState(value: String) extends Command
    private case object SaveSuccess extends Command
    private final case class DBError(cause: Throwable) extends Command

    def apply(id: String, db: DB): Behavior[Command] =
      Behaviors.withStash(10) { buffer =>
        Behaviors.setup { context =>
          new DataAccess(context, buffer, id, db).start()
        }
      }
  }

  class DataAccess(context: ActorContext[DataAccess.Command],
                   buffer: StashBuffer[DataAccess.Command],
                   id: String,
                   db: DB) {
    import DataAccess._


    def saving(state: String, replyTo: ActorRef[Done]): Behavior[Command] =
      Behaviors.receiveMessage {
        case SaveSuccess =>
          context.log.info("saving save success {}", state)
          replyTo ! Done
          buffer.unstashAll(active(state))
        case DBError(cause) =>
          throw cause
        case other =>
          context.log.info("saving other {} {}", state, other)
          buffer.stash(other)
          Behaviors.same
      }

    private def active(state: String): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Get(replyTo) =>
          context.log.info("active Get {}", state)
          replyTo ! state
          Behaviors.same
        case Save(value, replyTo) =>
          context.log.info("active Save {} {}", state, value)
          context.pipeToSelf(db.save(id, value)) {
            case Success(_) => SaveSuccess
            case Failure(cause) => DBError(cause)
          }
          saving(value, replyTo)
      }

    private def start(): Behavior[Command] = {
      context.log.info("pipe")
      context.pipeToSelf(db.load(id)) {
        case Success(value) => InitialState(value)
        case Failure(cause) => DBError(cause)
      }
      context.log.info("to")

      Behaviors.receiveMessage {
        case InitialState(value) =>
          context.log.info("initial state {}", value)
          buffer.unstashAll(active(value))
        case DBError(cause) =>
          throw cause
        case other =>
          context.log.info("start other {}", other)
          buffer.stash(other)
          Behaviors.same
      }
    }

  }

}
