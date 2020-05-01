package com.github.yoshiyoshifujii.akka.samples.remote

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Backend extends App {
  val conf =
    """
      |akka {
      |  actor {
      |    provider = cluster
      |  }
      |
      |  remote {
      |    artery {
      |      transport = tcp
      |      canonical {
      |        hostname = "127.0.0.1"
      |        port = 25520
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  val config = ConfigFactory.parseString(conf)
  val backend = ActorSystem("backend", config)

  class Simple extends Actor {
    override def receive: Receive = {
      case m => println(s"received $m!")
    }
  }

  backend.actorOf(Props[Simple], "simple")
}

object Frontend extends App {
  val conf =
    """
      |akka {
      |  actor {
      |    provider = cluster
      |  }
      |
      |  remote {
      |    artery {
      |      transport = tcp
      |      canonical {
      |        hostname = "127.0.0.1"
      |        port = 25521
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  val config = ConfigFactory.parseString(conf)
  val frontend = ActorSystem("frontend", config)

  val path = "akka://backend@127.0.0.1:25520/user/simple"
  val simple = frontend.actorSelection(path)

  simple ! "Hello Remote World!"
}
