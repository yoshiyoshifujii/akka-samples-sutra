package com.github.yoshiyoshifujii.akka.sample.dispatchers

import akka.actor.typed.{ ActorSystem, Behavior, DispatcherSelector }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContext, Future }

object Blocking extends App {

  object BlockingActor {

    def apply(): Behavior[Int] =
      Behaviors.receiveMessage { i =>
        Thread.sleep(5000)
        println("Blocking operation finished:", i)
        Behaviors.same
      }
  }

  object PrintActor {

    def apply(): Behavior[Int] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage { i =>
          context.log.info("Print actor: {} - {}", i, context.executionContext)
          Behaviors.same
        }
      }
  }

  private def triggerFutureBlockingOperation(
      i: Int
  )(implicit ec: ExecutionContext, context: ActorContext[_]): Future[Unit] = {
    context.log.info("Calling blocking Future: {} - {}", i, ec)
    Future {
      Thread.sleep(5000)
      println("Blocking future finished:", i, ec)
    }
  }

  object BlockingFutureActor {

    def apply(): Behavior[Int] =
      Behaviors.setup { implicit context =>
        implicit val executionContext: ExecutionContext =
          context.executionContext // The key problematic line here is this:
        Behaviors.receiveMessage { i =>
          triggerFutureBlockingOperation(i)
          Behaviors.same
        }
      }
  }

  object SeparateDispatcherFutureActor {

    def apply(): Behavior[Int] =
      Behaviors.setup { implicit context =>
        implicit val ex: ExecutionContext =
          context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))
        Behaviors.receiveMessage { i =>
          triggerFutureBlockingOperation(i)
          Behaviors.same
        }
      }
  }

  def patten1(): Unit = {
    object Guardian {
      def apply(): Behavior[Nothing] =
        Behaviors.setup[Nothing] { context =>
          (1 to 100).foreach { i =>
            context.spawnAnonymous(BlockingActor()) ! i
            context.spawnAnonymous(PrintActor()) ! i
          }
          Behaviors.empty
        }
    }

    val system = ActorSystem[Nothing](Guardian(), "blocking-pattern1")

    sys.addShutdownHook {
      system.terminate()
    }
  }

  def patten2(): Unit = {
    object Guardian {
      def apply(): Behavior[Nothing] =
        Behaviors.setup[Nothing] { context =>
          (1 to 100).foreach { i =>
            context.spawnAnonymous(BlockingFutureActor()) ! i
            context.spawnAnonymous(PrintActor()) ! i
          }
          Behaviors.empty
        }
    }

    val system = ActorSystem[Nothing](Guardian(), "blocking-pattern2")

    sys.addShutdownHook {
      system.terminate()
    }
  }

  def patten3(): Unit = {
    object Guardian {
      def apply(): Behavior[Nothing] =
        Behaviors.setup[Nothing] { context =>
          (1 to 100).foreach { i =>
            context.spawnAnonymous(SeparateDispatcherFutureActor()) ! i
            context.spawnAnonymous(PrintActor()) ! i
          }
          Behaviors.empty
        }
    }

    val system = ActorSystem[Nothing](
      Guardian(),
      "blocking-pattern3",
      ConfigFactory
        .parseString("""
        |my-blocking-dispatcher {
        |  type = Dispatcher
        |  executor = "thread-pool-executor"
        |  thread-pool-executor {
        |    fixed-pool-size = 16
        |  }
        |  throughput = 1
        |}
        |""".stripMargin).withFallback(ConfigFactory.load)
    )

    sys.addShutdownHook {
      system.terminate()
    }
  }

  patten3()
//  patten2()
//  patten1()

}
