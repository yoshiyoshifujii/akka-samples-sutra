package com.github.yoshiyoshifujii.samples.sendFutureResultToSelf

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App {

  trait CustomerDataAccess {
    def update(value: Customer): Future[Done]
  }

  final case class Customer(id: String, version: Long, name: String, address: String)

  object CustomRepository {
    sealed trait Command
    final case class Update(value: Customer, replyTo: ActorRef[UpdateResult]) extends Command
    private final case class WrappedUpdateResult(result: UpdateResult, replyTo: ActorRef[UpdateResult]) extends Command

    sealed trait UpdateResult
    final case class UpdateSuccess(id: String) extends UpdateResult
    final case class UpdateFailure(id: String, reason: String) extends UpdateResult

    private val MaxOperationsInProgress = 10

    def apply(dataAccess: CustomerDataAccess): Behavior[Command] =
      next(dataAccess, operationsInProgress = 0)

    private def next(dataAccess: CustomerDataAccess, operationsInProgress: Int): Behavior[Command] =
      Behaviors.receive { (context, command) =>
        command match {
          case Update(value, replyTo) =>
            if (operationsInProgress == MaxOperationsInProgress) {
              replyTo ! UpdateFailure(value.id, s"Max $MaxOperationsInProgress concurrent operations supported")
              Behaviors.same
            } else {
              val futureResult = dataAccess.update(value)
              context.pipeToSelf(futureResult) {
                case Success(_) => WrappedUpdateResult(UpdateSuccess(value.id), replyTo)
                case Failure(e) => WrappedUpdateResult(UpdateFailure(value.id, e.getMessage), replyTo)
              }
            }
            next(dataAccess, operationsInProgress + 1)
          case WrappedUpdateResult(result, replyTo) =>
            replyTo ! result
            next(dataAccess, operationsInProgress - 1)
        }
      }
  }

  val system = ActorSystem(CustomRepository((value: Customer) => {
    println(value)
    Future.successful(Done)
  }), "send-future-result-to-self")

  import akka.actor.typed.scaladsl.AskPattern._
  implicit val timeout: Timeout = Timeout(1.seconds)
  implicit val scheduler: Scheduler = system.scheduler

  val customer1 = Customer("id-1", 1L, "name-1", "address-1")

  val future = system.ask[CustomRepository.UpdateResult](reply => CustomRepository.Update(customer1, reply))
  val result = Await.result(future, Duration.Inf)
  result match {
    case CustomRepository.UpdateSuccess(id) =>
      system.log.info("succeeded. {}", id)
    case CustomRepository.UpdateFailure(id, reason) =>
      system.log.error("failed. id:{} reason:{}", id, reason)
  }

  system.terminate()

}
