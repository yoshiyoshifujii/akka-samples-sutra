val akkaVersion = "2.6.6"

lazy val root = (project in file("."))
  .enablePlugins(ProtocPlugin)
  .settings(
    name := "akka-sample-cluster",
    version := "0.1",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
        "com.typesafe.akka"    %% "akka-actor-typed"           % akkaVersion,
        "com.typesafe.akka"    %% "akka-slf4j"                 % akkaVersion,
        "com.typesafe.akka"    %% "akka-remote"                % akkaVersion,
        "com.typesafe.akka"    %% "akka-serialization-jackson" % akkaVersion,
        "ch.qos.logback"        % "logback-classic"            % "1.2.3",
        "com.typesafe.akka"    %% "akka-actor-testkit-typed"   % akkaVersion                             % Test,
        "org.scalatest"        %% "scalatest"                  % "3.2.0"                                 % Test,
        "com.beachape"         %% "enumeratum"                 % "1.6.1",
        "com.thesamet.scalapb" %% "scalapb-runtime"            % scalapb.compiler.Version.scalapbVersion % "protobuf"
      ),
    PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"
      )
  )
