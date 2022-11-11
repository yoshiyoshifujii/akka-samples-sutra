val AkkaVersion = "2.7.0"

lazy val root = (project in file("."))
  .settings(
    name := "akka-sample-supervisor",
    version := "0.1",
    scalaVersion := "2.13.10",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"         % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"               % AkkaVersion,
      "ch.qos.logback"     % "logback-classic"          % "1.4.4",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest"     %% "scalatest"                % "3.2.14"    % Test
    )
  )
