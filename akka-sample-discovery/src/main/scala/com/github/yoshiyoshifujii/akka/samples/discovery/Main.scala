package com.github.yoshiyoshifujii.akka.samples.discovery

import akka.actor.ActorSystem
import akka.discovery.{Discovery, Lookup, ServiceDiscovery}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Main extends App {

  val system: ActorSystem = ActorSystem("AkkaDiscoverySample", ConfigFactory.parseString(
    s"""
       |akka {
       |  discovery {
       |    method = akka-dns
       |  }
       |}
       |""".stripMargin).withFallback(ConfigFactory.load()))
  val serviceDiscovery: ServiceDiscovery = Discovery(system).discovery

  import system.dispatcher

  val lookup: Future[ServiceDiscovery.Resolved] = serviceDiscovery.lookup(Lookup("akka.io"), 1.second)
    .map { a =>
      println(a)
      a
    }
  Await.result(lookup, 5.seconds)

}
