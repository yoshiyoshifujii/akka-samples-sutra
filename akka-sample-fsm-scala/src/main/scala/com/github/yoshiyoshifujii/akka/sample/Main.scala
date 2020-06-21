package com.github.yoshiyoshifujii.akka.sample

import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object Main extends App {

  object DrinkingHakkers {
    def apply(): Behavior[NotUsed] =
      Behaviors.setup { context =>
        val chopsticks = for (i <- 1 to 5)  yield context.spawn(Chopstick(), s"Chopstick$i")

        val hakkers = for {
          (name, i) <- List("Ghosh", "Boner", "Klang", "Krasser", "Manie").zipWithIndex
        } yield
          context.spawn(Hakker(name, chopsticks(i), chopsticks((i + 1) % 5)), name)

        hakkers.foreach { hakker =>
          hakker ! Hakker.Think
        }

        Behaviors.empty
      }
  }

  val system = ActorSystem(DrinkingHakkers(), "DrinkingHakkers")

  sys.addShutdownHook {
    system.terminate()
  }

}
