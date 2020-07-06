package com.github.yoshiyoshifujii.akka.sample.persistence.testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

class PersistenceTestKitSampleSpec
    extends ScalaTestWithActorTestKit(
      PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication())
    )
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  override protected def beforeEach(): Unit = {
    persistenceTestKit.clearAll()
  }

  "Persistent actor" should {

    import SampleBehavior._

    "persist all events" in {

      val persistenceId   = PersistenceId.ofUniqueId("your-persistence-id-1")
      val persistentActor = spawn(SampleBehavior(persistenceId))
      val cmd             = Cmd("data")

      persistentActor ! cmd

      val expectedPersistedEvent = Evt(cmd.data)
      persistenceTestKit.expectNextPersisted(persistenceId.id, expectedPersistedEvent)
    }

  }
}
