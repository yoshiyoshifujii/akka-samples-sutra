package sample.cqrs

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.{Config, ConfigFactory}

object Main extends App {

  private def config(port: Int, httpPort: Int): Config =
    ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = $port
         |shopping.http.port = $httpPort
         |""".stripMargin).withFallback(ConfigFactory.load())

  private def startNode(port: Int, httpPort: Int): Unit = {
    val system = ActorSystem[Nothing](Guardian(), "Shopping", config(port, httpPort))

    if (Cluster(system).selfMember.hasRole("read-model"))
      CassandraHelper.createTables(system)
  }

  private def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }

  args.headOption match {
    case Some(portString) if portString.matches("""\d+""") =>
      val port = portString.toInt
      val httpPort = ("80" + portString.takeRight(2)).toInt
      startNode(port, httpPort)

    case Some("cassandra") =>
      startCassandraDatabase()
      println("Started Cassandra, press Ctrl+c to kill")
      new CountDownLatch(1).await()

    case None =>
      throw new IllegalArgumentException("port number, or cassandra required argument")
  }

}
