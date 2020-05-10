package sample.cqrs

import akka.actor.typed.ActorSystem
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry

import scala.concurrent.Await
import scala.concurrent.duration._

object CassandraHelper {
  val KeyspaceName = "akka_cqrs_sample"
  val OffsetTableName = "offsetStore"

  def createTables(system: ActorSystem[_]): Unit = {
    val session =
      CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

    val keyspaceStmt =
      s"""
         |CREATE KEYSPACE IF NOT EXISTS $KeyspaceName
         | WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }""".stripMargin

    val offsetTableStmt =
      s"""
         |CREATE TABLE IF NOT EXISTS $KeyspaceName.$OffsetTableName (
         |  eventProcessorId text,
         |  tag text,
         |  timeUuidOffset timeuuid,
         |  PRIMARY KEY (eventProcessorId, tag)
         |)""".stripMargin

    Await.ready(session.executeDDL(keyspaceStmt), 30.seconds)
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
  }

}
