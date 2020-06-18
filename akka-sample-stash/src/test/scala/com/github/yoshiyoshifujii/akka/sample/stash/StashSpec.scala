package com.github.yoshiyoshifujii.akka.sample.stash

import akka.Done
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.{Behaviors, StashOverflowException}
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class StashSpec extends AnyFreeSpec with BeforeAndAfterAll with ScalaFutures {
  private val testKit: ActorTestKit = ActorTestKit()

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(60, Seconds)), interval = scaled(Span(1, Seconds)))

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  object DBActor {
    sealed trait Reply
    final case class ReplySuccess(value: String) extends Reply
    case object ReplyNotFound extends Reply

    sealed trait Command
    final case class Save(id: String, value: String, replyTo: ActorRef[Done]) extends Command
    final case class Load(id: String, replyTo: ActorRef[Reply]) extends Command

    def saved(dbMap: Map[String, String]): Behavior[Command] =
      Behaviors.receiveMessage {
        case Save(id, value, replyTo) =>
          val behavior = saved(dbMap + (id -> value))
          replyTo ! Done
          behavior
        case Load(id, replyTo) =>
          replyTo ! dbMap.get(id).map(ReplySuccess).getOrElse(ReplyNotFound)
          Behaviors.same
      }

    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        saved(Map.empty[String, String])
      }
  }

  "Stash" - {
    import Stash._

    implicit val ex: ExecutionContext = testKit.system.executionContext

    import akka.actor.typed.scaladsl.AskPattern._
    implicit val timeout: Timeout = Timeout(1.seconds)
    implicit val scheduler: Scheduler = testKit.system.scheduler


    def generate: DB = new DB {
      private val dbActorRef: ActorRef[DBActor.Command] = testKit.spawn(DBActor())

      override def save(id: String, value: String): Future[Done] =
        dbActorRef.ask[Done](DBActor.Save(id, value, _))

      override def load(id: String): Future[String] = {
        dbActorRef.ask[DBActor.Reply](DBActor.Load(id, _)).map {
          case DBActor.ReplySuccess(value) => value
          case DBActor.ReplyNotFound => throw new RuntimeException(s"not found. $id")
        }
      }
    }

    "success" in {
      val localDb = generate
      localDb.save("id-1", "value-1")
      val dataAccessRef = testKit.spawn(DataAccess("id-1", localDb))

      assert(dataAccessRef.ask[String](DataAccess.Get).futureValue === "value-1")
      val futures = (1 to 11).map { i =>
        dataAccessRef.ask[Done](DataAccess.Save(s"value-$i", _))
      }
      assert(Future.sequence(futures).futureValue.size === 11)
      assert(dataAccessRef.ask[String](DataAccess.Get).futureValue === "value-11")
    }

    "stash over" in {
      val localDb = generate
      localDb.save("id-2", "value-0")
      val dataAccessRef = testKit.spawn(DataAccess("id-2", localDb))

      assert(dataAccessRef.ask[String](DataAccess.Get).futureValue === "value-0")
      (1 to 12).map { i =>
        dataAccessRef.ask[Done](DataAccess.Save(s"value-$i", _))
      }
      assertThrows[org.scalatest.exceptions.TestFailedException] {
        dataAccessRef.ask[String](DataAccess.Get).futureValue
      }
    }

  }
}
