val akkaVersion = "2.6.8"

lazy val root = (project in file("."))
  .settings(
    name := "akka-sample-reliable-delivery",
    organization := "com.github.yoshiyoshifujii.akka.samples",
    version := "0.1",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j"               % akkaVersion,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
        "ch.qos.logback"     % "logback-classic"          % "1.2.3" excludeAll (
          ExclusionRule(organization = "org.slf4j")
        ),
        "org.scalatest" %% "scalatest" % "3.2.0" % Test
      )
  )
