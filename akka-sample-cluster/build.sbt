val akkaVersion = "2.6.7"

lazy val root = (project in file("."))
  .enablePlugins(ProtocPlugin)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(
    name := "akka-sample-cluster",
    version := "0.1",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j"                 % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed"                % akkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
        "ch.qos.logback"     % "logback-classic"            % "1.2.3" excludeAll (
          ExclusionRule(organization = "org.slf4j")
        ),
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
        "com.typesafe.akka" %% "akka-multi-node-testkit"  % akkaVersion % Test,
        "org.scalatest"     %% "scalatest"                % "3.2.0"     % Test,
        "com.beachape"      %% "enumeratum"               % "1.6.1"
      ),
    PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"
      )
  )
