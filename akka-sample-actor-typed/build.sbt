val AkkaVersion = "2.6.5"

lazy val root = (project in file("."))
  .settings(
    name := "akka-sample-actor-typed",
    version := "0.1",
    scalaVersion := "2.13.2",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )
