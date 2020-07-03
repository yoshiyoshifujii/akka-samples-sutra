val akkaVersion = "2.6.6"

lazy val root = (project in file("."))
  .settings(
    name := "akka-sample-persistence",
    version := "0.1",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
        "com.typesafe.akka" %% "akka-persistence-typed"     % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j"                 % akkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
        "ch.qos.logback"     % "logback-classic"            % "1.2.3",
        "com.typesafe.akka" %% "akka-actor-testkit-typed"   % akkaVersion % Test,
        "com.typesafe.akka" %% "akka-persistence-testkit"   % akkaVersion % Test,
        "org.scalatest"     %% "scalatest"                  % "3.2.0"     % Test
      )
  )
