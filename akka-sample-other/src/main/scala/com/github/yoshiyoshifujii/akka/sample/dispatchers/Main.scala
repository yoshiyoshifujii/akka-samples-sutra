package com.github.yoshiyoshifujii.akka.sample.dispatchers

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object Main extends App {

  object YourBehavior {
    sealed trait Command
    final object Something extends Command
    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        context.log.info("context.executionContext is {}", context.executionContext)
        Behaviors.receiveMessage {
          case Something =>
            Behaviors.same
        }
      }
  }

  object Guardian {
    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>
        import akka.actor.typed.DispatcherSelector
        implicit val exe = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-dispatcher"))
        println(exe)

        context.spawn(YourBehavior(), "DefaultDispatcher")
        context.spawn(YourBehavior(), "ExplicitDefaultDispatcher", DispatcherSelector.default())
        context.spawn(YourBehavior(), "BlockingDispatcher", DispatcherSelector.blocking())
        context.spawn(YourBehavior(), "ParentDispatcher", DispatcherSelector.sameAsParent())
        context.spawn(YourBehavior(), "DispatcherFromConfig", DispatcherSelector.fromConfig("your-dispatcher"))

        Behaviors.same
      }

  }

  val system = ActorSystem(Guardian(), "dispatchers", ConfigFactory.parseString(
    """
      |my-dispatcher {
      |  type = "Dispatcher"
      |  executor = "default-executor"
      |  default-executor {
      |    fallback = "fork-join-executor"
      |  }
      |  fork-join-executor {
      |    parallelism-min = 8
      |    parallelism-factor = 1.0
      |    parallelism-max = 64
      |    task-peeking-mode = "FIFO"
      |  }
      |  shutdown-timeout = 1s
      |  throughput = 5
      |  throughput-deadline-time = 0ms
      |  attempt-teamwork = on
      |  mailbox-requirement = ""
      |}
      |your-dispatcher {
      |  type = Dispatcher
      |  executor = "thread-pool-executor"
      |  thread-pool-executor {
      |    fixed-pool-size = 32
      |  }
      |  throughput = 1
      |}
      |""".stripMargin).withFallback(ConfigFactory.load()))

  system.terminate()
}
